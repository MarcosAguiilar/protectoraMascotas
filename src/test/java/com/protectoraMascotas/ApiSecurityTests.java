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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.protectoraMascotas.data.MascotaRepository;

@Transactional
class ApiSecurityTests extends SecurityTestSupport {

    @Autowired private MockMvc mvc;
    @Autowired private MascotaRepository repository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private Environment environment;
    @Autowired private ApplicationContext context;

    @ParameterizedTest
    @ValueSource(strings = {"/api/mascotas", "/api/mascotas/1", "/api/mascotas/nombre/Luna",
            "/api/mascotas/paged20", "/api/mascotas/paged/0"})
    void consultasPublicas(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isOk());
    }

    @Test
    void consultaInexistenteConserva404() throws Exception {
        mvc.perform(get("/api/mascotas/999999")).andExpect(status().isNotFound());
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
                        .header(token.get("headerName").asText(), token.get("token").asText())
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
        assertThat(createdMascota.path("nombre").asText()).isEqualTo("Prueba seguridad");
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
                        .header(token.get("headerName").asText(), token.get("token").asText()))
                .andExpect(status().isNoContent());
        assertThat(repository.findById(id)).isEmpty();
        assertThat(repository.count()).isEqualTo(count);
        mvc.perform(get("/api/mascotas/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void tokenYDocumentacionRequierenAutenticacion() throws Exception {
        mvc.perform(get("/api/csrf")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v2/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/v2/api-docs").with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    void consolaDeshabilitadaEnProduccionInclusoParaUsuarioAutenticado() throws Exception {
        assertThat(environment.getProperty("spring.h2.console.enabled", Boolean.class)).isFalse();
        assertThat(context.containsBean("h2Console")).isFalse();
        mvc.perform(get("/h2-console/").with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isForbidden());
    }
}
