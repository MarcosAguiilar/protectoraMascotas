package com.protectoraMascotas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.MethodArgumentNotValidException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.protectoraMascotas.data.MascotaRepository;
import com.protectoraMascotas.entity.Mascota;

@Transactional
class MascotaDtoIntegrationTests extends SecurityTestSupport {

    @Autowired private MockMvc mvc;
    @Autowired private MascotaRepository repository;
    @Autowired private JsonMapper jsonMapper;

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"nombre\":null}", "{\"nombre\":\"\"}",
            "{\"nombre\":\"  \"}", "{\"nombre\":\"\\t\\n\"}"})
    void nombreInvalidoDevuelve400SinGuardar(String json) throws Exception {
        assertValidationFailure(json, "nombre");
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void pesoNoPositivoDevuelve400SinGuardar(long peso) throws Exception {
        assertValidationFailure("{\"nombre\":\"Luna\",\"peso\":" + peso + "}", "peso");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"nombre\":\" Luna \"}", "{\"nombre\":\" Luna \",\"peso\":null}"})
    void pesoOpcionalYCamposAusentesConservanSusValores(String json) throws Exception {
        MvcResult result = create(json);
        JsonNode response = jsonMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.get("id").longValue()).isPositive();
        assertThat(response.get("nombre").stringValue()).isEqualTo(" Luna ");
        assertThat(response.get("peso").isNull()).isTrue();
        assertThat(response.get("fechaNac").isNull()).isTrue();
        assertThat(response.get("raza").isNull()).isTrue();
        assertThat(response.get("url_foto").isNull()).isTrue();
        assertThat(response.get("tiene_chip").booleanValue()).isFalse();
        assertThat(response.size()).isEqualTo(7);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, Long.MAX_VALUE})
    void pesoPositivoSeAcepta(long peso) throws Exception {
        MvcResult result = create("{\"nombre\":\"Nueva\",\"peso\":" + peso + "}");
        assertThat(jsonMapper.readTree(result.getResponse().getContentAsString()).get("peso").longValue())
                .isEqualTo(peso);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 999999})
    void idDelClienteSeIgnoraSinActualizarOtraMascota(long clientId) throws Exception {
        long count = repository.count();
        MvcResult result = create("{\"id\":" + clientId + ",\"nombre\":\"Otra mascota\",\"peso\":10}");
        long generatedId = jsonMapper.readTree(result.getResponse().getContentAsString()).get("id").longValue();

        assertThat(generatedId).isPositive().isNotEqualTo(clientId);
        assertThat(repository.count()).isEqualTo(count + 1);
        assertThat(repository.findById(1L).orElseThrow().getNombre()).isEqualTo("Luna");
        assertThat(repository.findById(generatedId).orElseThrow().getNombre()).isEqualTo("Otra mascota");
        if (clientId == 999999) {
            assertThat(repository.findById(clientId)).isEmpty();
        }
    }

    @Test
    void altaConservaNombresJsonFechasYTodosLosValores() throws Exception {
        MvcResult result = create("""
                {"nombre":"Nueva","fechaNac":"2020-05-01T12:00:00.000+00:00",
                 "raza":"Mestiza","peso":12,"tiene_chip":true,"url_foto":"www.example.com/nueva.jpg"}
                """);
        JsonNode response = jsonMapper.readTree(result.getResponse().getContentAsString());
        long id = response.get("id").longValue();
        assertThat(response.size()).isEqualTo(7);
        assertThat(response.get("fechaNac").stringValue()).isEqualTo("2020-05-01T12:00:00.000+00:00");
        assertThat(response.get("tiene_chip").booleanValue()).isTrue();
        assertThat(response.get("url_foto").stringValue()).isEqualTo("www.example.com/nueva.jpg");
        assertThat(response.get("tieneChip")).isNull();
        assertThat(response.get("urlFoto")).isNull();
        Mascota stored = repository.findById(id).orElseThrow();
        assertThat(stored.getFechaNac().toInstant()).isEqualTo(Instant.parse("2020-05-01T12:00:00Z"));
        assertThat(stored.getRaza()).isEqualTo("Mestiza");
        assertThat(stored.getPeso()).isEqualTo(12L);
        assertThat(stored.isTiene_chip()).isTrue();
        assertThat(stored.getUrl_foto()).isEqualTo("www.example.com/nueva.jpg");
        MvcResult read = mvc.perform(get("/api/mascotas/" + id)).andExpect(status().isOk()).andReturn();
        assertThat(jsonMapper.readTree(read.getResponse().getContentAsString())).isEqualTo(response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/mascotas", "/api/mascotas/1", "/api/mascotas/nombre/Luna",
            "/api/mascotas/paged20", "/api/mascotas/paged/0"})
    void dtoDeLecturaConservaElJsonAnteriorDeLasEntidades(String path) throws Exception {
        Object previousResponse = switch (path) {
            case "/api/mascotas" -> repository.findAll().stream().map(MascotaDtoIntegrationTests::legacyResponse).toList();
            case "/api/mascotas/1" -> legacyResponse(repository.findById(1L).orElseThrow());
            case "/api/mascotas/nombre/Luna" -> repository.findByNombre("Luna").stream().map(MascotaDtoIntegrationTests::legacyResponse).toList();
            case "/api/mascotas/paged20" -> repository.findAllByOrderByFechaNacDesc(PageRequest.of(0, 20)).getContent()
                    .stream().map(MascotaDtoIntegrationTests::legacyResponse).toList();
            case "/api/mascotas/paged/0" -> repository.findAll(PageRequest.of(0, 5)).map(MascotaDtoIntegrationTests::legacyResponse);
            default -> throw new IllegalArgumentException(path);
        };
        // Comparar JSON transmitido, no LongNode/IntNode creados desde tipos Java distintos.
        JsonNode expected = jsonMapper.readTree(jsonMapper.writeValueAsString(previousResponse));
        MvcResult result = mvc.perform(get(path)).andExpect(status().isOk()).andReturn();

        assertThat(jsonMapper.readTree(result.getResponse().getContentAsString())).isEqualTo(expected);
    }

    @Test
    void openApiDocumentaNombreObligatorioYEntradaSinId() throws Exception {
        MvcResult result = mvc.perform(get("/v3/api-docs").with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.CrearMascotaRequest.properties.id").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.MascotaResponse.properties.id").exists())
                .andExpect(jsonPath("$.components.schemas.CrearMascotaRequest.properties.tiene_chip").exists())
                .andReturn();
        JsonNode schema = jsonMapper.readTree(result.getResponse().getContentAsString())
                .get("components").get("schemas").get("CrearMascotaRequest");
        assertThat(schema.get("required").toString()).contains("\"nombre\"");
    }

    private MvcResult create(String json) throws Exception {
        return mvc.perform(post("/api/mascotas").with(httpBasic(USERNAME, PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn();
    }

    // Congelar los siete campos del contrato anterior: la entidad ya tiene una asociación nueva.
    private static MascotaAnterior legacyResponse(Mascota entity) {
        return new MascotaAnterior(entity.getId(), entity.getNombre(), entity.getFechaNac(), entity.getRaza(),
                entity.getPeso(), entity.isTiene_chip(), entity.getUrl_foto());
    }

    private record MascotaAnterior(Long id, String nombre, java.util.Date fechaNac, String raza, Long peso,
            @com.fasterxml.jackson.annotation.JsonProperty("tiene_chip") boolean tieneChip,
            @com.fasterxml.jackson.annotation.JsonProperty("url_foto") String urlFoto) {}

    private void assertValidationFailure(String json, String field) throws Exception {
        long count = repository.count();
        MvcResult result = mvc.perform(post("/api/mascotas").with(httpBasic(USERNAME, PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(result.getResolvedException()).isInstanceOf(MethodArgumentNotValidException.class);
        MethodArgumentNotValidException failure = (MethodArgumentNotValidException) result.getResolvedException();
        assertThat(failure.getBindingResult().hasFieldErrors(field)).isTrue();
        assertThat(repository.count()).isEqualTo(count);
    }
}
