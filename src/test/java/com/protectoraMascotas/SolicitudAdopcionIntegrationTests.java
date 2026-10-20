package com.protectoraMascotas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;

import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.protectoraMascotas.data.MascotaRepository;
import com.protectoraMascotas.data.ProtectoraRepository;
import com.protectoraMascotas.data.SolicitudAdopcionRepository;
import com.protectoraMascotas.entity.EstadoSolicitud;
import com.protectoraMascotas.entity.Mascota;
import com.protectoraMascotas.entity.Protectora;
import com.protectoraMascotas.mapper.SolicitudAdopcionMapper;
import com.protectoraMascotas.service.SolicitudAdopcionService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Sin transacción de test: comprobar DTOs después de cerrar la sesión del servicio.
class SolicitudAdopcionIntegrationTests extends SecurityTestSupport {

    private static final String BASE = "/api/solicitudes-adopcion";
    @Autowired private MockMvc mvc;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private MascotaRepository mascotas;
    @Autowired private ProtectoraRepository protectoras;
    @Autowired private SolicitudAdopcionRepository repository;
    @Autowired private SolicitudAdopcionService service;
    @Autowired private SolicitudAdopcionMapper mapper;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private Environment environment;
    private Long mascotaId;
    private Long sinProtectoraId;
    private Long protectoraId;

    @BeforeEach
    void fixtures() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            String name = "solicitud-test-" + UUID.randomUUID();
            Protectora protectora = new Protectora();
            protectora.setNombre(name);
            protectoraId = protectoras.save(protectora).getId();
            Mascota mascota = new Mascota();
            mascota.setNombre(name);
            mascota.setProtectora(protectora);
            mascotaId = mascotas.save(mascota).getId();
            Mascota otra = new Mascota();
            otra.setNombre(name);
            sinProtectoraId = mascotas.save(otra).getId();
        });
    }

    @AfterEach
    void removeOnlyOwnFixtures() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            List<Long> ids = List.of(mascotaId, sinProtectoraId);
            entityManager.createQuery("delete from SolicitudAdopcion s where s.mascota.id in :ids")
                    .setParameter("ids", ids).executeUpdate();
            mascotas.deleteAllByIdInBatch(ids);
            protectoras.deleteAllByIdInBatch(List.of(protectoraId));
        });
    }

    @Test
    void altaPersisteYConsultaAutenticadaConservaLosSeisCampos() throws Exception {
        Instant before = Instant.now().minusMillis(1);
        JsonNode body = created(validJson());
        assertThat(body.size()).isEqualTo(6);
        assertThat(body.get("id").asLong()).isPositive();
        assertThat(body.get("mascotaId").asLong()).isEqualTo(mascotaId);
        assertThat(body.get("nombreSolicitante").asText()).isEqualTo("Nombre Privado");
        assertThat(body.get("emailSolicitante").asText()).isEqualTo("privado@example.org");
        assertThat(body.get("estado").asText()).isEqualTo("PENDIENTE");
        Instant date = Instant.parse(body.get("fechaSolicitud").asText());
        assertThat(date).isBetween(before, Instant.now());
        var stored = repository.findById(body.get("id").asLong()).orElseThrow();
        assertThat(stored.getNombreSolicitante()).isEqualTo("Nombre Privado");
        assertThat(stored.getEmailSolicitante()).isEqualTo("privado@example.org");
        assertThat(stored.getFechaSolicitud()).isEqualTo(date);
        assertThat(stored.getEstado()).isEqualTo(EstadoSolicitud.PENDIENTE);
        for (String suffix : new String[]{"", "/"}) {
            mvc.perform(get(BASE + "/" + body.get("id").asLong() + suffix).with(httpBasic(USERNAME, PASSWORD)))
                    .andExpect(status().isOk()).andExpect(content().json(body.toString()))
                    .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"APROBADA", "RECHAZADA"})
    void clienteNoPuedeAsignarIdFechaNiEstado(String state) throws Exception {
        long existing = created(validJson()).get("id").asLong();
        String body = validJson().replace("}", ",\"id\":" + existing
                + ",\"fechaSolicitud\":\"1900-01-01T00:00:00Z\",\"estado\":\"" + state + "\"}");
        JsonNode response = created(body);
        assertThat(response.get("id").asLong()).isNotEqualTo(existing);
        assertThat(response.get("estado").asText()).isEqualTo("PENDIENTE");
        assertThat(Instant.parse(response.get("fechaSolicitud").asText())).isNotEqualTo(Instant.parse("1900-01-01T00:00:00Z"));
        assertThat(repository.count()).isEqualTo(2);
        assertThat(repository.findById(existing).orElseThrow().getEstado()).isEqualTo(EstadoSolicitud.PENDIENTE);
    }

    @Test
    void mascotaInexistenteDevuelve404() throws Exception {
        problem(create(validJson().replace(mascotaId.toString(), "9223372036854775807")), 404)
                .andExpect(jsonPath("$.title").value("Mascota no encontrada"));
        assertThat(repository.count()).isZero();
    }

    @Test
    void mascotaSinProtectoraDevuelve409() throws Exception {
        problem(create(validJson().replace(mascotaId.toString(), sinProtectoraId.toString())), 409)
                .andExpect(jsonPath("$.title").value("Mascota sin protectora"))
                .andExpect(jsonPath("$.detail").value("No se puede registrar la solicitud: la mascota no tiene una protectora asignada."));
        assertThat(repository.count()).isZero();
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void validacionIndicaCampoYMensajeSinDatosPersonales(String template, String field, String message) throws Exception {
        problem(create(template.replace("#ID#", mascotaId.toString())), 400)
                .andExpect(jsonPath("$.errors[?(@.field == '" + field + "')].message", org.hamcrest.Matchers.hasItem(message)));
        assertThat(repository.count()).isZero();
    }

    static Stream<Arguments> invalidRequests() {
        String name = "\"nombreSolicitante\":\"Nombre Privado\"";
        String email = "\"emailSolicitante\":\"privado@example.org\"";
        String id = "\"mascotaId\":#ID#";
        return Stream.of(
                Arguments.of("{" + name + "," + email + "}", "mascotaId", "El ID de la mascota es obligatorio"),
                Arguments.of("{\"mascotaId\":null," + name + "," + email + "}", "mascotaId", "El ID de la mascota es obligatorio"),
                Arguments.of("{\"mascotaId\":0," + name + "," + email + "}", "mascotaId", "El ID de la mascota debe ser positivo"),
                Arguments.of("{\"mascotaId\":-1," + name + "," + email + "}", "mascotaId", "El ID de la mascota debe ser positivo"),
                Arguments.of("{" + id + "," + email + "}", "nombreSolicitante", "El nombre del solicitante es obligatorio y no puede contener solo espacios"),
                Arguments.of("{" + id + ",\"nombreSolicitante\":\"  \"," + email + "}", "nombreSolicitante", "El nombre del solicitante es obligatorio y no puede contener solo espacios"),
                Arguments.of("{" + id + ",\"nombreSolicitante\":\"" + "N".repeat(151) + "\"," + email + "}", "nombreSolicitante", "La longitud del campo supera el máximo permitido"),
                Arguments.of("{" + id + "," + name + "}", "emailSolicitante", "El email del solicitante es obligatorio"),
                Arguments.of("{" + id + "," + name + ",\"emailSolicitante\":null}", "emailSolicitante", "El email del solicitante es obligatorio"),
                Arguments.of("{" + id + "," + name + ",\"emailSolicitante\":\"\"}", "emailSolicitante", "El email del solicitante es obligatorio"),
                Arguments.of("{" + id + "," + name + ",\"emailSolicitante\":\"dato privado\"}", "emailSolicitante", "El email del solicitante debe tener un formato válido"),
                Arguments.of("{" + id + "," + name + ",\"emailSolicitante\":\"" + "e".repeat(255) + "\"}", "emailSolicitante", "La longitud del campo supera el máximo permitido"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "", "{\"mascotaId\":\"dato privado\"}", "{\"mascotaId\":9223372036854775808}"})
    void jsonInvalidoEs400Seguro(String json) throws Exception { problem(create(json), 400); }

    @Test
    void solicitudInexistenteEs404SoloTrasAutenticarse() throws Exception {
        problem(mvc.perform(get(BASE + "/9223372036854775807").with(httpBasic(USERNAME, PASSWORD))), 404)
                .andExpect(jsonPath("$.title").value("Solicitud de adopción no encontrada"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/", "/1", "/1/", "/9223372036854775807"})
    void ningunaConsultaEsPublica(String path) throws Exception {
        mvc.perform(get(BASE + path)).andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"));
    }

    @Test
    void credencialesIncorrectasNoRevelanInformacion() throws Exception {
        created(validJson());
        mvc.perform(get(BASE).with(httpBasic(USERNAME, PASSWORD + "-incorrecta")))
                .andExpect(status().isUnauthorized()).andExpect(content().string(""));
        mvc.perform(post(BASE).with(csrf()).with(httpBasic(USERNAME, PASSWORD + "-incorrecta"))
                .contentType(MediaType.APPLICATION_JSON).content(validJson())).andExpect(status().isUnauthorized());
    }

    @Test
    void postRequiereBasicYCsrfAntesDeLaValidacion() throws Exception {
        mvc.perform(post(BASE).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(validJson()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(BASE).with(httpBasic(USERNAME, PASSWORD)).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isForbidden());
        mvc.perform(post(BASE).with(httpBasic(USERNAME, PASSWORD)).with(csrf().useInvalidToken())
                .contentType(MediaType.APPLICATION_JSON).content(validJson())).andExpect(status().isForbidden());
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(validJson())).andExpect(status().isForbidden());
        assertThat(repository.count()).isZero();
    }

    @Test
    void tokenCsrfRealPermiteRegistrar() throws Exception {
        var tokenResult = mvc.perform(get("/api/csrf").with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        JsonNode token = jsonMapper.readTree(tokenResult.getResponse().getContentAsString());
        MockHttpSession session = (MockHttpSession) tokenResult.getRequest().getSession(false);
        mvc.perform(post(BASE).with(httpBasic(USERNAME, PASSWORD)).session(session)
                .header(token.get("headerName").asText(), token.get("token").asText())
                .contentType(MediaType.APPLICATION_JSON).content(validJson()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    void listaVaciaYDefaultsSonEstables() throws Exception {
        mvc.perform(get(BASE).with(httpBasic(USERNAME, PASSWORD))).andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty()).andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0)).andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void paginacionOrdenadaNoExponeEntidades() throws Exception {
        long first = created(validJson()).get("id").asLong();
        long second = created(validJson()).get("id").asLong();
        long third = created(validJson()).get("id").asLong();
        mvc.perform(get(BASE + "?page=0&size=2").with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].id").value(first)).andExpect(jsonPath("$.content[1].id").value(second))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.last").value(false)).andExpect(jsonPath("$.content[0].mascota").doesNotExist());
        mvc.perform(get(BASE + "/?page=1&size=2").with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(third))
                .andExpect(jsonPath("$.first").value(false)).andExpect(jsonPath("$.last").value(true));
        mvc.perform(get(BASE + "?page=100&size=100").with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"page=-1", "size=0", "size=-1", "size=101", "page=2147483647&size=100",
            "page=texto", "size=texto", "page=2147483648"})
    void paginacionInvalidaProduce400(String query) throws Exception {
        problem(mvc.perform(get(BASE + "?" + query).with(httpBasic(USERNAME, PASSWORD))), 400);
    }

    @Test
    void lecturaSinOsivEvitaNMasUnoYMapperDetachedNoConsulta() throws Exception {
        for (int i = 0; i < 3; i++) { created(validJson()); }
        assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            mvc.perform(get(BASE + "?size=2").with(httpBasic(USERNAME, PASSWORD))).andExpect(status().isOk());
            assertThat(statistics.getPrepareStatementCount()).isBetween(1L, 2L);
            var page = service.findAll(0, 2);
            page.getContent().forEach(s -> assertThat(Hibernate.isInitialized(s.getMascota())).isTrue());
            statistics.clear();
            mapper.toPage(page);
            assertThat(statistics.getPrepareStatementCount()).isZero();
        } finally { statistics.setStatisticsEnabled(false); }
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void noSeRegistranDatosPersonalesEnLogs(CapturedOutput output) throws Exception {
        created(validJson());
        problem(create(validJson().replace("privado@example.org", "dato privado")), 400);
        assertThat(output.getAll()).doesNotContain("Nombre Privado", "privado@example.org", "dato privado");
        assertThat(environment.getProperty("logging.level.org.hibernate.orm.jdbc.bind")).isEqualTo("OFF");
    }

    @Test
    void cambiosDeEstadoYBorradosNoEstanDisponibles() throws Exception {
        mvc.perform(patch(BASE + "/1").with(httpBasic(USERNAME, PASSWORD)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"APROBADA\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete(BASE + "/1").with(httpBasic(USERNAME, PASSWORD)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void openApiDocumentaAutenticacionCamposYErrores() throws Exception {
        mvc.perform(get("/v3/api-docs").with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/solicitudes-adopcion'].get.security[0].basicAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/solicitudes-adopcion/{id}'].get.security[0].basicAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/solicitudes-adopcion'].post.security[0].basicAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/solicitudes-adopcion'].post.responses['409'].content['application/problem+json']").exists())
                .andExpect(jsonPath("$.components.schemas.CrearSolicitudAdopcionRequest.required",
                        org.hamcrest.Matchers.containsInAnyOrder("mascotaId", "nombreSolicitante", "emailSolicitante")))
                .andExpect(jsonPath("$.components.schemas.CrearSolicitudAdopcionRequest.properties.id").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CrearSolicitudAdopcionRequest.properties.fechaSolicitud").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CrearSolicitudAdopcionRequest.properties.estado").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.SolicitudAdopcionResponse.properties.fechaSolicitud.format").value("date-time"))
                .andExpect(jsonPath("$.components.schemas.SolicitudAdopcionResponse.properties.estado.enum",
                        org.hamcrest.Matchers.containsInAnyOrder("PENDIENTE", "APROBADA", "RECHAZADA")));
    }

    private String validJson() {
        return "{\"mascotaId\":" + mascotaId + ",\"nombreSolicitante\":\"Nombre Privado\",\"emailSolicitante\":\"privado@example.org\"}";
    }

    private ResultActions create(String json) throws Exception {
        return mvc.perform(post(BASE).with(httpBasic(USERNAME, PASSWORD)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private JsonNode created(String json) throws Exception {
        return jsonMapper.readTree(create(json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private ResultActions problem(ResultActions result, int code) throws Exception {
        result.andExpect(status().is(code)).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank")).andExpect(jsonPath("$.status").value(code))
                .andExpect(jsonPath("$.title").isNotEmpty()).andExpect(jsonPath("$.detail").isNotEmpty());
        assertThat(result.andReturn().getResponse().getContentAsString()).doesNotContain(
                "Nombre Privado", "privado@example.org", "dato privado", "rejectedValue", "java.lang", "org.hibernate", "Exception", "trace");
        return result;
    }
}
