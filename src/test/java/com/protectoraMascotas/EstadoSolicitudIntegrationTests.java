package com.protectoraMascotas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.protectoraMascotas.data.MascotaRepository;
import com.protectoraMascotas.data.ProtectoraRepository;
import com.protectoraMascotas.data.SolicitudAdopcionRepository;
import com.protectoraMascotas.entity.*;
import com.protectoraMascotas.exception.ConflictoEstadoSolicitudException;
import com.protectoraMascotas.service.SolicitudAdopcionService;

// Sin transacción de test: cada petición/hilo confirma o revierte su propia transacción.
class EstadoSolicitudIntegrationTests extends SecurityTestSupport {

    @Autowired private MockMvc mvc;
    @Autowired private MascotaRepository mascotas;
    @Autowired private ProtectoraRepository protectoras;
    @Autowired private SolicitudAdopcionRepository solicitudes;
    @Autowired private SolicitudAdopcionService service;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private EntityManager entityManager;
    private Long mascotaId;
    private Long protectoraId;
    private Long primeraId;
    private Long segundaId;

    @BeforeEach
    void fixtures() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            Protectora protectora = new Protectora();
            protectora.setNombre("estados-" + UUID.randomUUID());
            protectoraId = protectoras.save(protectora).getId();
            Mascota mascota = new Mascota();
            mascota.setNombre("Mascota de prueba");
            mascota.setProtectora(protectora);
            mascotaId = mascotas.save(mascota).getId();
            primeraId = solicitudes.save(pendiente(mascota)).getId();
            segundaId = solicitudes.save(pendiente(mascota)).getId();
        });
    }

    private SolicitudAdopcion pendiente(Mascota mascota) {
        SolicitudAdopcion solicitud = new SolicitudAdopcion();
        solicitud.setMascota(mascota);
        solicitud.setNombreSolicitante("Persona privada");
        solicitud.setEmailSolicitante("privado@example.org");
        solicitud.setFechaSolicitud(Instant.parse("2026-01-01T00:00:00Z"));
        solicitud.setEstado(EstadoSolicitud.PENDIENTE);
        return solicitud;
    }

    @AfterEach
    void removeOnlyOwnFixtures() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            entityManager.createQuery("delete from SolicitudAdopcion s where s.mascota.id = :id")
                    .setParameter("id", mascotaId).executeUpdate();
            mascotas.deleteAllByIdInBatch(List.of(mascotaId));
            protectoras.deleteAllByIdInBatch(List.of(protectoraId));
        });
    }

    @ParameterizedTest
    @EnumSource(value = EstadoSolicitud.class, names = {"APROBADA", "RECHAZADA"})
    void transicionValidaPersisteYConservaContratoYDatos(EstadoSolicitud estado) throws Exception {
        mvc.perform(patch(path(primeraId)).with(httpBasic(USERNAME, PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(estado)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$.id").value(primeraId))
                .andExpect(jsonPath("$.mascotaId").value(mascotaId))
                .andExpect(jsonPath("$.estado").value(estado.name()))
                .andExpect(jsonPath("$.nombreSolicitante").value("Persona privada"))
                .andExpect(jsonPath("$.emailSolicitante").value("privado@example.org"))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        var persisted = solicitudes.findById(primeraId).orElseThrow();
        assertThat(persisted.getEstado()).isEqualTo(estado);
        assertThat(persisted.getFechaSolicitud()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
        assertEstado(segundaId, EstadoSolicitud.PENDIENTE);
        mvc.perform(get("/api/solicitudes-adopcion/" + primeraId).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value(estado.name()));
    }

    @ParameterizedTest
    @CsvSource({"APROBADA,APROBADA", "APROBADA,RECHAZADA", "RECHAZADA,APROBADA", "RECHAZADA,RECHAZADA"})
    void finalesRechazanCualquierTransicion(EstadoSolicitud anterior, EstadoSolicitud destino) throws Exception {
        service.cambiarEstado(primeraId, anterior);
        problemPatch(primeraId, json(destino), 409);
        assertEstado(primeraId, anterior);
        assertEstado(segundaId, EstadoSolicitud.PENDIENTE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"estado\":null}", "{\"estado\":\"PENDIENTE\"}",
            "{\"estado\":\"OTRO\"}", "{\"estado\":0}", "{\"estado\":true}",
            "{\"estado\":[]}", "{\"estado\":{}}", "{\"estado\":\"aprobada\"}",
            "{", "{\"estado\":\"APROBADA\",\"emailSolicitante\":\"privado@example.org\"}"})
    void entradaInvalidaDevuelve400SinCambiarDatos(String body) throws Exception {
        problemPatch(primeraId, body, 400);
        assertEstado(primeraId, EstadoSolicitud.PENDIENTE);
        assertEstado(segundaId, EstadoSolicitud.PENDIENTE);
    }

    @Test
    void solicitudInexistenteDevuelve404() throws Exception {
        problemPatch(Long.MAX_VALUE, json(EstadoSolicitud.APROBADA), 404);
    }

    @Test
    void idNoNumericoDevuelve400() throws Exception {
        mvc.perform(patch("/api/solicitudes-adopcion/no-numero/estado")
                        .with(httpBasic(USERNAME, PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(EstadoSolicitud.APROBADA)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void otraAprobacionYAltaDevuelven409PeroRechazoSiguePermitido() throws Exception {
        service.cambiarEstado(primeraId, EstadoSolicitud.APROBADA);
        problemPatch(segundaId, json(EstadoSolicitud.APROBADA), 409);
        mvc.perform(post("/api/solicitudes-adopcion").with(httpBasic(USERNAME, PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mascotaId\":" + mascotaId + ",\"nombreSolicitante\":\"Persona\",\"emailSolicitante\":\"nuevo@example.org\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("La mascota ya tiene una solicitud de adopción aprobada."));
        assertEstado(segundaId, EstadoSolicitud.PENDIENTE);
        assertThat(ownRequests()).hasSize(2);
        service.cambiarEstado(segundaId, EstadoSolicitud.RECHAZADA);
        assertEstado(segundaId, EstadoSolicitud.RECHAZADA);
    }

    @Test
    void rechazarNoImpideRegistrarNiAprobarOtraSolicitud() throws Exception {
        service.cambiarEstado(primeraId, EstadoSolicitud.RECHAZADA);
        mvc.perform(post("/api/solicitudes-adopcion").with(httpBasic(USERNAME, PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mascotaId\":" + mascotaId + ",\"nombreSolicitante\":\"Persona\",\"emailSolicitante\":\"nuevo@example.org\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("PENDIENTE"));
        service.cambiarEstado(segundaId, EstadoSolicitud.APROBADA);
        assertEstado(primeraId, EstadoSolicitud.RECHAZADA);
        assertEstado(segundaId, EstadoSolicitud.APROBADA);
        assertThat(ownRequests()).filteredOn(s -> s.getEstado() == EstadoSolicitud.PENDIENTE).hasSize(1);
    }

    @Test
    void estadoAusenteIdentificaElCampoSinExponerValores() throws Exception {
        mvc.perform(patch(path(primeraId)).with(httpBasic(USERNAME, PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("estado"))
                .andExpect(jsonPath("$.errors[0].message").value("El estado es obligatorio y debe ser APROBADA o RECHAZADA"))
                .andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist());
    }

    @Test
    void patchRequiereBasicYCsrf() throws Exception {
        mvc.perform(patch(path(primeraId)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(EstadoSolicitud.APROBADA)))
                .andExpect(status().isUnauthorized()).andExpect(header().exists("WWW-Authenticate"));
        mvc.perform(patch(path(primeraId)).with(httpBasic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(json(EstadoSolicitud.APROBADA)))
                .andExpect(status().isForbidden());
        mvc.perform(patch(path(primeraId)).with(httpBasic(USERNAME, PASSWORD)).with(csrf().useInvalidToken())
                        .contentType(MediaType.APPLICATION_JSON).content(json(EstadoSolicitud.APROBADA)))
                .andExpect(status().isForbidden());
        mvc.perform(patch(path(primeraId)).with(httpBasic(USERNAME, PASSWORD + "wrong")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(EstadoSolicitud.APROBADA)))
                .andExpect(status().isUnauthorized());
        assertEstado(primeraId, EstadoSolicitud.PENDIENTE);
    }

    @Test
    void patchFuncionaConTokenCsrfReal() throws Exception {
        var csrfResponse = mvc.perform(get("/api/csrf").with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        var token = tools.jackson.databind.json.JsonMapper.builder().build()
                .readTree(csrfResponse.getResponse().getContentAsString());
        mvc.perform(patch(path(primeraId)).with(httpBasic(USERNAME, PASSWORD))
                        .session((org.springframework.mock.web.MockHttpSession) csrfResponse.getRequest().getSession(false))
                        .header(token.get("headerName").asString(), token.get("token").asString())
                        .contentType(MediaType.APPLICATION_JSON).content(json(EstadoSolicitud.RECHAZADA)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("RECHAZADA"));
    }

    @Test
    void openApiDocumentaPatchYSuSeguridad() throws Exception {
        mvc.perform(get("/v3/api-docs").with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/solicitudes-adopcion/{id}/estado'].patch.security[0].basicAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/solicitudes-adopcion/{id}/estado'].patch.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/solicitudes-adopcion/{id}/estado'].patch.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/solicitudes-adopcion/{id}/estado'].patch.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/solicitudes-adopcion/{id}/estado'].patch.responses['409']").exists())
                .andExpect(jsonPath("$.paths['/api/solicitudes-adopcion/{id}/estado'].patch.parameters[?(@.name == 'X-CSRF-TOKEN')].required",
                        org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.components.schemas.CambiarEstadoSolicitudRequest.properties.estado.enum",
                        org.hamcrest.Matchers.containsInAnyOrder("APROBADA", "RECHAZADA")))
                .andExpect(jsonPath("$.components.schemas.CambiarEstadoSolicitudRequest.additionalProperties").value(false));
    }

    @RepeatedTest(3)
    void aprobacionesConcurrentesSoloPermitenUna() throws Exception {
        var results = race(() -> service.cambiarEstado(primeraId, EstadoSolicitud.APROBADA),
                () -> service.cambiarEstado(segundaId, EstadoSolicitud.APROBADA));
        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(ownRequests()).filteredOn(s -> s.getEstado() == EstadoSolicitud.APROBADA).hasSize(1);
        assertThat(ownRequests()).filteredOn(s -> s.getEstado() == EstadoSolicitud.PENDIENTE).hasSize(1);
    }

    @Test
    void dosCambiosSimultaneosDeLaMismaSolicitudSoloPermitenUno() throws Exception {
        assertThat(race(() -> service.cambiarEstado(primeraId, EstadoSolicitud.APROBADA),
                () -> service.cambiarEstado(primeraId, EstadoSolicitud.RECHAZADA)))
                .containsExactlyInAnyOrder(true, false);
        assertThat(solicitudes.findById(primeraId).orElseThrow().getEstado()).isNotEqualTo(EstadoSolicitud.PENDIENTE);
    }

    @Test
    void altaConcurrenteEsperaLaAprobacionYNoInserta() throws Exception {
        // La primera transacción retiene el bloqueo hasta que la segunda inicia su operación.
        var locked = new CountDownLatch(1);
        var registrationStarted = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var approval = pool.submit(() -> new TransactionTemplate(transactions).execute(tx -> {
                mascotas.findByIdForUpdate(mascotaId).orElseThrow();
                locked.countDown();
                await(registrationStarted);
                service.cambiarEstado(primeraId, EstadoSolicitud.APROBADA);
                return true;
            }));
            var registration = pool.submit(() -> {
                await(locked);
                registrationStarted.countDown();
                return attempt(() -> service.save(pendiente(null), mascotaId));
            });
            assertThat(approval.get(15, TimeUnit.SECONDS)).isTrue();
            assertThat(registration.get(15, TimeUnit.SECONDS)).isFalse();
            assertThat(ownRequests()).hasSize(2);
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    private List<Boolean> race(Callable<?> first, Callable<?> second) throws Exception {
        var start = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> { start.await(10, TimeUnit.SECONDS); return attempt(first); });
            var b = pool.submit(() -> { start.await(10, TimeUnit.SECONDS); return attempt(second); });
            return List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
            assertThat(pool.awaitTermination(15, TimeUnit.SECONDS)).isTrue();
        }
    }

    private boolean attempt(Callable<?> action) throws Exception {
        try { action.call(); return true; }
        catch (ConflictoEstadoSolicitudException expected) { return false; }
    }

    private static void await(CountDownLatch latch) {
        try { assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue(); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssertionError(ex); }
    }

    private List<SolicitudAdopcion> ownRequests() {
        return new TransactionTemplate(transactions).execute(tx -> entityManager.createQuery(
                "select s from SolicitudAdopcion s where s.mascota.id = :id", SolicitudAdopcion.class)
                .setParameter("id", mascotaId).getResultList());
    }

    private void problemPatch(Long id, String body, int expectedStatus) throws Exception {
        var result = mvc.perform(patch(path(id)).with(httpBasic(USERNAME, PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.instance").value(path(id)))
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.trace").doesNotExist()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(
                "Persona privada", "privado@example.org", "SQL", "Exception", "org.hibernate", "java.lang");
    }

    private void assertEstado(Long id, EstadoSolicitud estado) {
        assertThat(solicitudes.findById(id).orElseThrow().getEstado()).isEqualTo(estado);
    }

    private String path(Long id) { return "/api/solicitudes-adopcion/" + id + "/estado"; }
    private String json(EstadoSolicitud estado) { return "{\"estado\":\"" + estado.name() + "\"}"; }
}
