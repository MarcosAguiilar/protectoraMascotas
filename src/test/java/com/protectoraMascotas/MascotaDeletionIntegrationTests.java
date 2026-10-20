package com.protectoraMascotas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.protectoraMascotas.data.MascotaRepository;
import com.protectoraMascotas.data.ProtectoraRepository;
import com.protectoraMascotas.data.SolicitudAdopcionRepository;
import com.protectoraMascotas.entity.EstadoSolicitud;
import com.protectoraMascotas.entity.Mascota;
import com.protectoraMascotas.entity.Protectora;
import com.protectoraMascotas.entity.SolicitudAdopcion;

// Sin @Transactional: comprobar los datos después del rollback/commit del DELETE HTTP.
class MascotaDeletionIntegrationTests extends SecurityTestSupport {

    @Autowired private MockMvc mvc;
    @Autowired private MascotaRepository mascotas;
    @Autowired private ProtectoraRepository protectoras;
    @MockitoSpyBean private SolicitudAdopcionRepository solicitudes;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private EntityManager entityManager;
    private Long mascotaId;
    private Long libreId;
    private Long protectoraId;
    private Long solicitudId;

    @BeforeEach
    void fixtures() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            String nombre = "delete-test-" + UUID.randomUUID();
            Protectora protectora = new Protectora();
            protectora.setNombre(nombre);
            protectoraId = protectoras.save(protectora).getId();
            Mascota mascota = new Mascota();
            mascota.setNombre(nombre);
            mascota.setProtectora(protectora);
            mascotaId = mascotas.save(mascota).getId();
            Mascota libre = new Mascota();
            libre.setNombre(nombre);
            libreId = mascotas.save(libre).getId();
            SolicitudAdopcion solicitud = new SolicitudAdopcion();
            solicitud.setMascota(mascota);
            solicitud.setNombreSolicitante("Persona Reservada");
            solicitud.setEmailSolicitante("reservado@example.org");
            solicitud.setFechaSolicitud(Instant.parse("2026-01-01T00:00:00Z"));
            solicitud.setEstado(EstadoSolicitud.PENDIENTE);
            solicitudId = solicitudes.save(solicitud).getId();
        });
    }

    @AfterEach
    void removeOnlyOwnFixtures() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            entityManager.createQuery("delete from SolicitudAdopcion s where s.id = :id")
                    .setParameter("id", solicitudId).executeUpdate();
            mascotas.deleteAllByIdInBatch(List.of(mascotaId, libreId));
            protectoras.deleteAllByIdInBatch(List.of(protectoraId));
        });
    }

    @ParameterizedTest
    @EnumSource(EstadoSolicitud.class)
    void mascotaConSolicitudesDevuelve409YConservaDatosEnCualquierEstado(EstadoSolicitud estado) throws Exception {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            solicitudes.findById(solicitudId).orElseThrow().setEstado(estado);
        });
        assertConflictAndPreservedData(estado);
    }

    @Test
    void mascotaSinSolicitudesDevuelve204YSeElimina() throws Exception {
        mvc.perform(delete(path(libreId)).with(httpBasic(USERNAME, PASSWORD)).with(csrf()))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(mascotas.existsById(libreId)).isFalse();
        assertThat(mascotas.existsById(mascotaId)).isTrue();
        assertThat(solicitudes.existsById(solicitudId)).isTrue();
    }

    @Test
    void mascotaInexistenteDevuelve404() throws Exception {
        mvc.perform(delete(path(Long.MAX_VALUE)).with(httpBasic(USERNAME, PASSWORD)).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Mascota no encontrada"));
        assertThat(mascotas.existsById(mascotaId)).isTrue();
        assertThat(solicitudes.existsById(solicitudId)).isTrue();
    }

    @Test
    void fkRealConservaLosDatosSiLaComprobacionDeExistenciaQuedaObsoleta() throws Exception {
        // Simular el resultado previo a una inserción concurrente; la FK de H2 es real.
        doReturn(false).when(solicitudes).existsByMascotaId(mascotaId);
        assertConflictAndPreservedData(EstadoSolicitud.PENDIENTE);
    }

    @Test
    void conflictoNoPermiteSaltarBasicNiCsrf() throws Exception {
        mvc.perform(delete(path(mascotaId)).with(csrf()))
                .andExpect(status().isUnauthorized()).andExpect(header().exists("WWW-Authenticate"));
        mvc.perform(delete(path(mascotaId)).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isForbidden());
        mvc.perform(delete(path(mascotaId)).with(httpBasic(USERNAME, PASSWORD)).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        assertThat(mascotas.existsById(mascotaId)).isTrue();
        assertThat(solicitudes.existsById(solicitudId)).isTrue();
    }

    @Test
    void openApiDocumentaLosTresResultadosYSeguridad() throws Exception {
        mvc.perform(get("/v3/api-docs").with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/mascotas/delete/{id}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/mascotas/delete/{id}'].delete.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/mascotas/delete/{id}'].delete.responses['409'].content['application/problem+json']").exists())
                .andExpect(jsonPath("$.paths['/api/mascotas/delete/{id}'].delete.security[0].basicAuth").isArray());
    }

    private void assertConflictAndPreservedData(EstadoSolicitud estado) throws Exception {
        long petsBefore = mascotas.count();
        long requestsBefore = solicitudes.count();
        var result = mvc.perform(delete(path(mascotaId)).with(httpBasic(USERNAME, PASSWORD)).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Mascota con solicitudes de adopción"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("No se puede eliminar la mascota porque tiene solicitudes de adopción asociadas."))
                .andExpect(jsonPath("$.instance").value(path(mascotaId)))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(
                "Persona Reservada", "reservado@example.org", "fk_solicitud", "SQL", "Exception", "org.hibernate", "java.lang");
        assertThat(mascotas.count()).isEqualTo(petsBefore);
        assertThat(solicitudes.count()).isEqualTo(requestsBefore);
        assertThat(mascotas.existsById(mascotaId)).isTrue();
        var solicitud = solicitudes.findById(solicitudId).orElseThrow();
        assertThat(solicitud.getMascota().getId()).isEqualTo(mascotaId);
        assertThat(solicitud.getNombreSolicitante()).isEqualTo("Persona Reservada");
        assertThat(solicitud.getEmailSolicitante()).isEqualTo("reservado@example.org");
        assertThat(solicitud.getFechaSolicitud()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(solicitud.getEstado()).isEqualTo(estado);
    }

    private String path(Long id) { return "/api/mascotas/delete/" + id; }
}
