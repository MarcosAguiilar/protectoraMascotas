package com.protectoraMascotas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.protectoraMascotas.data.MascotaRepository;

@Transactional
class ApiSecurityTests extends SecurityTestSupport {

    @Autowired private MockMvc mvc;
    @Autowired private MascotaRepository repository;
    @Autowired private JsonMapper objectMapper;
    @Autowired private Environment environment;
    @Autowired private ApplicationContext context;

    @ParameterizedTest
    @ValueSource(strings = {"/api/mascotas", "/api/mascotas/1", "/api/mascotas/nombre/Luna",
            "/api/mascotas/paged20", "/api/mascotas/paged/0"})
    void consultasPublicas(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/mascotas/", "/api/mascotas/1/", "/api/mascotas/nombre/Luna/",
            "/api/mascotas/paged20/", "/api/mascotas/paged/0/"})
    void consultasConBarraFinalConservanCompatibilidad(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    @Test
    void consultaInexistenteConserva404() throws Exception {
        mvc.perform(get("/api/mascotas/999999")).andExpect(status().isNotFound());
    }

    @Test
    void consultaConservaLosNombresDelModeloJson() throws Exception {
        String expectedDate = java.time.format.DateTimeFormatter
                .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxx")
                .withZone(java.time.ZoneOffset.UTC)
                .format(repository.findById(1L).orElseThrow().getFechaNac().toInstant());
        MvcResult result = mvc.perform(get("/api/mascotas/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Luna"))
                .andExpect(jsonPath("$.fechaNac").value(expectedDate))
                .andExpect(jsonPath("$.tiene_chip").value(true))
                .andExpect(jsonPath("$.url_foto").value("www.example.com/luna.jpg"))
                .andReturn();
        JsonNode mascota = objectMapper.readTree(result.getResponse().getContentAsString());
        java.util.Set<String> fields = new java.util.HashSet<>();
        mascota.properties().forEach(field -> fields.add(field.getKey()));
        assertThat(fields).containsExactlyInAnyOrder("id", "nombre", "fechaNac", "raza", "peso",
                "tiene_chip", "url_foto");
    }

    @Test
    void paginacionConservaElContratoDeRespuesta() throws Exception {
        mvc.perform(get("/api/mascotas/paged/0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(5))
                .andExpect(jsonPath("$.totalElements").value(49))
                .andExpect(jsonPath("$.totalPages").value(10))
                .andExpect(jsonPath("$.number").value(0))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.pageable.pageNumber").value(0))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    void busquedaSinResultadosConserva204() throws Exception {
        MvcResult result = mvc.perform(get("/api/mascotas/nombre/NoExiste"))
                .andExpect(status().isNoContent()).andReturn();
        assertThat(result.getResponse().getContentAsString()).isEmpty();
    }

    @Test
    void tokenCsrfDeOtraSesionNoAutorizaEscrituras() throws Exception {
        MvcResult result = mvc.perform(get("/api/csrf").with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        JsonNode token = objectMapper.readTree(result.getResponse().getContentAsString());
        long count = repository.count();
        mvc.perform(post("/api/mascotas").session(new MockHttpSession())
                        .with(httpBasic(USERNAME, PASSWORD))
                        .header(token.get("headerName").stringValue(), token.get("token").stringValue())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"No autorizada\"}"))
                .andExpect(status().isForbidden());
        assertThat(repository.count()).isEqualTo(count);
    }

    @Test
    void borrarMascotaInexistenteDevuelve404() throws Exception {
        mvc.perform(delete("/api/mascotas/delete/999999")
                        .with(httpBasic(USERNAME, PASSWORD)).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Mascota no encontrada"));
    }

    @ParameterizedTest
    @CsvSource({"POST,/api/mascotas", "DELETE,/api/mascotas/delete/1"})
    void escrituraSinAutenticacionConCsrfDevuelve401(String method, String path) throws Exception {
        long count = repository.count();
        mvc.perform(request(HttpMethod.valueOf(method), path).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"Prueba\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().exists("WWW-Authenticate"));
        assertThat(repository.count()).isEqualTo(count);
        assertThat(repository.findById(1L)).isPresent();
    }

    @ParameterizedTest
    @CsvSource({"POST,/api/mascotas", "DELETE,/api/mascotas/delete/1"})
    void escrituraAnonimaSinCsrfDevuelve403(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({"POST,/api/mascotas", "DELETE,/api/mascotas/delete/1"})
    void credencialesIncorrectasDevuelven401(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path).with(csrf())
                        .with(httpBasic(USERNAME, PASSWORD + "-invalid"))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @CsvSource({"POST,/api/mascotas", "DELETE,/api/mascotas/delete/1"})
    void usuarioAutenticadoNecesitaCsrf(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path).with(httpBasic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(request(HttpMethod.valueOf(method), path).with(httpBasic(USERNAME, PASSWORD))
                        .with(csrf().useInvalidToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void crearYBorrarConBasicYTokenReal() throws Exception {
        MvcResult tokenResult = mvc.perform(get("/api/csrf").with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk()).andExpect(jsonPath("token").isNotEmpty()).andReturn();
        JsonNode token = objectMapper.readTree(tokenResult.getResponse().getContentAsString());
        MockHttpSession session = (MockHttpSession) tokenResult.getRequest().getSession(false);
        assertThat(session).isNotNull();
        long count = repository.count();

        MvcResult created = mvc.perform(post("/api/mascotas").session(session)
                        .with(httpBasic(USERNAME, PASSWORD))
                        .header(token.get("headerName").stringValue(), token.get("token").stringValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Prueba seguridad\",\"peso\":10}"))
                .andExpect(status().isCreated()).andReturn();
        String responseBody = created.getResponse().getContentAsString();
        assertThat(responseBody).as("POST debe devolver la mascota creada en JSON").isNotBlank();
        JsonNode createdMascota = objectMapper.readTree(responseBody);
        assertThat(createdMascota.isObject()).as("Se esperaba un objeto JSON: %s", responseBody).isTrue();
        JsonNode idNode = createdMascota.get("id");
        assertThat(idNode).as("La respuesta de POST debe incluir id: %s", responseBody).isNotNull();
        assertThat(idNode.isIntegralNumber() && idNode.canConvertToLong())
                .as("El id debe ser un entero representable como Long: %s", responseBody).isTrue();
        long id = idNode.longValue();
        assertThat(id).as("El id generado debe ser positivo").isPositive();
        assertThat(createdMascota.path("nombre").stringValue()).isEqualTo("Prueba seguridad");
        assertThat(createdMascota.path("peso").asLong()).isEqualTo(10L);
        assertThat(repository.count()).isEqualTo(count + 1);
        assertThat(repository.findById(id)).isPresent();
        mvc.perform(get("/api/mascotas/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.nombre").value("Prueba seguridad"))
                .andExpect(jsonPath("$.peso").value(10));

        mvc.perform(delete("/api/mascotas/delete/" + id).session(session)
                        .with(httpBasic(USERNAME, PASSWORD))
                        .header(token.get("headerName").stringValue(), token.get("token").stringValue()))
                .andExpect(status().isNoContent());
        assertThat(repository.findById(id)).isEmpty();
        assertThat(repository.count()).isEqualTo(count);
        mvc.perform(get("/api/mascotas/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void tokenYDocumentacionRequierenAutenticacion() throws Exception {
        mvc.perform(get("/api/csrf")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v3/api-docs").with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").value("3.0.1"))
                .andExpect(jsonPath("$.components.securitySchemes.basicAuth.scheme").value("basic"))
                .andExpect(jsonPath("$.paths['/api/mascotas'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/mascotas'].post.security[0].basicAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/mascotas'].post.parameters[0].name").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.paths['/api/mascotas/delete/{id}'].delete.security[0].basicAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/csrf'].get.security[0].basicAuth").isArray());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/swagger-ui/index.html", "/swagger-ui/swagger-ui-bundle.js",
            "/v3/api-docs/swagger-config", "/v3/api-docs.yaml"})
    void recursosDeDocumentacionRequierenAutenticacion(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).with(httpBasic(USERNAME, PASSWORD))).andExpect(status().isOk());
    }

    @Test
    void entradaSwaggerConservaRutaYAutenticacion() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized());
        mvc.perform(get("/swagger-ui.html").with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/swagger-ui/index.html"));
    }

    @Test
    void consolaDeshabilitadaEnProduccionInclusoParaUsuarioAutenticado() throws Exception {
        assertThat(environment.getProperty("spring.h2.console.enabled", Boolean.class)).isFalse();
        assertThat(context.containsBean("h2Console")).isFalse();
        mvc.perform(get("/h2-console/").with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isForbidden());
    }
}
