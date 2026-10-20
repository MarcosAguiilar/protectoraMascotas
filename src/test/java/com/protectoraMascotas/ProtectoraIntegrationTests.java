package com.protectoraMascotas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.protectoraMascotas.data.ProtectoraRepository;
import com.protectoraMascotas.dto.CrearProtectoraRequest;
import com.protectoraMascotas.entity.Protectora;
import com.protectoraMascotas.mapper.ProtectoraMapper;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Transactional
class ProtectoraIntegrationTests extends SecurityTestSupport {

    @Autowired private MockMvc mvc;
    @Autowired private ProtectoraRepository repository;
    @Autowired private ProtectoraMapper mapper;
    @Autowired private EntityManager entityManager;
    @Autowired private JsonMapper jsonMapper;

    @ParameterizedTest
    @ValueSource(strings = {"/api/protectoras", "/api/protectoras/"})
    void listaInicialVaciaEsPublica(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void altaCompletaPersisteTodosLosCamposYLosGetSonPublicos() throws Exception {
        String json = jsonMapper.writeValueAsString(new CrearProtectoraRequest(
                " Refugio ", "contacto@example.org", "+34 900 000 000", "Madrid"));
        MvcResult result = create(json).andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$.nombre").value(" Refugio "))
                .andExpect(jsonPath("$.email").value("contacto@example.org"))
                .andExpect(jsonPath("$.telefono").value("+34 900 000 000"))
                .andExpect(jsonPath("$.municipio").value("Madrid")).andReturn();
        JsonNode response = jsonMapper.readTree(result.getResponse().getContentAsString());
        long id = response.get("id").asLong();
        assertThat(id).isPositive();
        repository.flush();
        entityManager.clear();
        Protectora loaded = repository.findById(id).orElseThrow();
        assertThat(loaded.getNombre()).isEqualTo(" Refugio ");
        assertThat(loaded.getEmail()).isEqualTo("contacto@example.org");
        assertThat(loaded.getTelefono()).isEqualTo("+34 900 000 000");
        assertThat(loaded.getMunicipio()).isEqualTo("Madrid");
        for (String path : new String[]{"/api/protectoras/" + id, "/api/protectoras/" + id + "/"}) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().json(response.toString()));
        }
        mvc.perform(get("/api/protectoras")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(id));
    }

    @Test
    void altaMinimaConservaCamposOpcionalesNulos() throws Exception {
        MvcResult result = create("{\"nombre\":\"Refugio\"}").andReturn();
        JsonNode response = jsonMapper.readTree(result.getResponse().getContentAsString());
        assertThat(response.size()).isEqualTo(5);
        assertThat(response.get("id").asLong()).isPositive();
        assertThat(response.get("email").isNull()).isTrue();
        assertThat(response.get("telefono").isNull()).isTrue();
        assertThat(response.get("municipio").isNull()).isTrue();
    }

    @Test
    void idEnviadoSeIgnoraSinActualizarUnaProtectoraExistente() throws Exception {
        Protectora existente = repository.saveAndFlush(mapper.toEntity(new CrearProtectoraRequest("Original", null, null, null)));
        long originalId = existente.getId();
        MvcResult result = create("{\"id\":" + originalId + ",\"nombre\":\"Nueva\"}").andReturn();
        long newId = jsonMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        assertThat(newId).isPositive().isNotEqualTo(originalId);
        repository.flush();
        entityManager.clear();
        assertThat(repository.findById(originalId).orElseThrow().getNombre()).isEqualTo("Original");
        assertThat(repository.count()).isEqualTo(2);
    }

    @Test
    void protectoraInexistenteDevuelve404ProblemDetail() throws Exception {
        problem(mvc.perform(get("/api/protectoras/999999")), 404, "/api/protectoras/999999")
                .andExpect(jsonPath("$.title").value("Protectora no encontrada"))
                .andExpect(jsonPath("$.detail").value("No existe una protectora con ID 999999."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"texto", "1.5", "9223372036854775808"})
    void idInvalidoDevuelve400(String id) throws Exception {
        String path = "/api/protectoras/" + id;
        problem(mvc.perform(get(path)), 400, path);
    }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void validacionDevuelve400SinInsertarNiExponerDatos(String json, String field) throws Exception {
        problem(mvc.perform(post("/api/protectoras").with(httpBasic(USERNAME, PASSWORD)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json)), 400, "/api/protectoras")
                .andExpect(jsonPath("$.errors[*].field", org.hamcrest.Matchers.hasItem(field)))
                .andExpect(jsonPath("$.errors[0].message").isNotEmpty());
        assertThat(repository.count()).isZero();
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(Arguments.of("{}", "nombre"),
                Arguments.of("{\"nombre\":null}", "nombre"),
                Arguments.of("{\"nombre\":\"\"}", "nombre"),
                Arguments.of("{\"nombre\":\"   \"}", "nombre"),
                Arguments.of("{\"nombre\":\"" + "N".repeat(151) + "\"}", "nombre"),
                Arguments.of("{\"nombre\":\"N\",\"email\":\"dato privado\"}", "email"),
                Arguments.of("{\"nombre\":\"N\",\"email\":\"" + "a".repeat(255) + "\"}", "email"),
                Arguments.of("{\"nombre\":\"N\",\"telefono\":\"" + "1".repeat(31) + "\"}", "telefono"),
                Arguments.of("{\"nombre\":\"N\",\"municipio\":\"" + "M".repeat(151) + "\"}", "municipio"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "", "{\"nombre\":{\"interno\":\"dato privado\"}}"})
    void jsonMalformadoOTipoIncorrectoDevuelve400(String json) throws Exception {
        problem(mvc.perform(post("/api/protectoras").with(httpBasic(USERNAME, PASSWORD)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json)), 400, "/api/protectoras");
        assertThat(repository.count()).isZero();
    }

    @Test
    void postSinAutenticacionConCsrfDevuelve401() throws Exception {
        mvc.perform(post("/api/protectoras").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Refugio\"}"))
                .andExpect(status().isUnauthorized()).andExpect(header().exists("WWW-Authenticate"));
        assertThat(repository.count()).isZero();
    }

    @Test
    void credencialesIncorrectasDevuelven401() throws Exception {
        mvc.perform(post("/api/protectoras").with(httpBasic(USERNAME, PASSWORD + "-incorrecta")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Refugio\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(repository.count()).isZero();
    }

    @Test
    void csrfAusenteOInvalidoDevuelve403AntesDeValidar() throws Exception {
        mvc.perform(post("/api/protectoras").with(httpBasic(USERNAME, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/protectoras").with(httpBasic(USERNAME, PASSWORD)).with(csrf().useInvalidToken())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Refugio\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/protectoras").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isForbidden());
        assertThat(repository.count()).isZero();
    }

    @Test
    void tokenRealPermiteRegistrarYConsultar() throws Exception {
        MvcResult tokenResult = mvc.perform(get("/api/csrf").with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        JsonNode token = jsonMapper.readTree(tokenResult.getResponse().getContentAsString());
        MockHttpSession session = (MockHttpSession) tokenResult.getRequest().getSession(false);
        assertThat(session).isNotNull();
        MvcResult created = mvc.perform(post("/api/protectoras").with(httpBasic(USERNAME, PASSWORD))
                        .session(session).header(token.get("headerName").asText(), token.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Token real\"}"))
                .andExpect(status().isCreated()).andReturn();
        long id = jsonMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        assertThat(id).isPositive();
        mvc.perform(get("/api/protectoras/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Token real"));
    }

    @Test
    void operacionesNoImplementadasSiguenDenegadas() throws Exception {
        mvc.perform(delete("/api/protectoras/1").with(httpBasic(USERNAME, PASSWORD)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/protectoras/1/privado").with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isForbidden());
    }

    @Test
    void openApiDescribeContratosValidacionErroresYSeguridad() throws Exception {
        mvc.perform(get("/v3/api-docs").with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/protectoras'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/protectoras/{id}'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/protectoras/{id}'].get.responses['200']").exists())
                .andExpect(jsonPath("$.paths['/api/protectoras/{id}'].get.responses['404'].content['application/problem+json']").exists())
                .andExpect(jsonPath("$.paths['/api/protectoras'].post.responses['201'].content['application/json'].schema['$ref']")
                        .value("#/components/schemas/ProtectoraResponse"))
                .andExpect(jsonPath("$.paths['/api/protectoras'].post.responses['400'].content['application/problem+json']").exists())
                .andExpect(jsonPath("$.paths['/api/protectoras'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/protectoras'].post.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/protectoras'].post.security[0].basicAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/protectoras'].post.parameters[?(@.name == 'X-CSRF-TOKEN')].required",
                        org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$.components.schemas.CrearProtectoraRequest.required", org.hamcrest.Matchers.hasItem("nombre")))
                .andExpect(jsonPath("$.components.schemas.CrearProtectoraRequest.properties.id").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.CrearProtectoraRequest.properties.nombre.maxLength").value(150))
                .andExpect(jsonPath("$.components.schemas.CrearProtectoraRequest.properties.email.maxLength").value(254))
                .andExpect(jsonPath("$.components.schemas.CrearProtectoraRequest.properties.email.format").value("email"))
                .andExpect(jsonPath("$.components.schemas.CrearProtectoraRequest.properties.telefono.maxLength").value(30))
                .andExpect(jsonPath("$.components.schemas.CrearProtectoraRequest.properties.municipio.maxLength").value(150));
    }

    private ResultActions create(String json) throws Exception {
        return mvc.perform(post("/api/protectoras").with(httpBasic(USERNAME, PASSWORD)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isCreated());
    }

    private ResultActions problem(ResultActions result, int code, String path) throws Exception {
        result.andExpect(status().is(code))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(code))
                .andExpect(jsonPath("$.title").isNotEmpty()).andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value(path));
        assertThat(result.andReturn().getResponse().getContentAsString())
                .doesNotContain("dato privado", "rejectedValue", "java.lang", "org.hibernate", "stackTrace", "Exception");
        return result;
    }
}
