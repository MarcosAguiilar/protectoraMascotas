package com.protectoraMascotas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.protectoraMascotas.data.MascotaRepository;

@Transactional
class ApiErrorIntegrationTests extends SecurityTestSupport {

    @Autowired private MockMvc mvc;
    @Autowired private MascotaRepository repository;

    @ParameterizedTest
    @ValueSource(strings = {"/api/mascotas/999999", "/api/mascotas/999999/"})
    void consultaInexistentePublicaDevuelveProblemDetail(String path) throws Exception {
        problem(mvc.perform(get(path)), 404, path)
                .andExpect(jsonPath("$.title").value("Mascota no encontrada"))
                .andExpect(jsonPath("$.detail").value("No existe una mascota con ID 999999."));
    }

    @Test
    void eliminacionInexistenteAutorizadaDevuelveProblemDetailSinAlterarFilas() throws Exception {
        long count = repository.count();
        String path = "/api/mascotas/delete/999999";
        problem(mvc.perform(delete(path).with(httpBasic(USERNAME, PASSWORD)).with(csrf())), 404, path);
        assertThat(repository.count()).isEqualTo(count);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "-2147483648", "texto", "1.5", "2147483648", "2147483647", "429496730"})
    void paginacionInvalidaDevuelve400(String page) throws Exception {
        String path = "/api/mascotas/paged/" + page;
        problem(mvc.perform(get(path)), 400, path);
    }

    @ParameterizedTest
    @ValueSource(ints = {100, 429496729})
    void paginaValidaSinResultadosConserva200YMetadatos(int page) throws Exception {
        mvc.perform(get("/api/mascotas/paged/" + page))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.number").value(page)).andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(49));
    }

    @Test
    void fallbackDeErroresInternosConserva500SinExponerDetalles() throws Exception {
        var failure = new org.springframework.dao.DataIntegrityViolationException("SQL secreto");
        var result = mvc.perform(get("/error").with(request -> {
                            request.setDispatcherType(jakarta.servlet.DispatcherType.ERROR);
                            return request;
                        })
                        .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_EXCEPTION, failure)
                        .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_MESSAGE, "SQL secreto")
                        .param("trace", "true").param("message", "true"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist()).andReturn();
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("SQL secreto", "DataIntegrityViolationException");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "", "{\"nombre\":\"Prueba\",\"peso\":\"SQL secreto\"}",
            "{\"nombre\":\"Prueba\",\"fechaNac\":\"fecha secreta\"}"})
    void jsonMalformadoOTiposIncorrectosDevuelven400SinFiltrarDatos(String json) throws Exception {
        long count = repository.count();
        problem(mvc.perform(post("/api/mascotas").with(httpBasic(USERNAME, PASSWORD)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json)), 400, "/api/mascotas")
                .andExpect(jsonPath("$.title").value("JSON no válido"));
        assertThat(repository.count()).isEqualTo(count);
    }

    @Test
    void validacionDetallaCamposSinIncluirValoresRechazados() throws Exception {
        problem(mvc.perform(post("/api/mascotas").with(httpBasic(USERNAME, PASSWORD)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"   \",\"peso\":-987654321}")),
                400, "/api/mascotas")
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[*].field", org.hamcrest.Matchers.containsInAnyOrder("nombre", "peso")))
                .andExpect(jsonPath("$.errors[*].message", org.hamcrest.Matchers.containsInAnyOrder(
                        "El nombre es obligatorio y no puede contener solo espacios", "El peso debe ser positivo")));
    }

    @Test
    void idNoNumericoDevuelve400SinExponerElTipoJava() throws Exception {
        problem(mvc.perform(get("/api/mascotas/texto")), 400, "/api/mascotas/texto");
        problem(mvc.perform(delete("/api/mascotas/delete/texto").with(httpBasic(USERNAME, PASSWORD)).with(csrf())),
                400, "/api/mascotas/delete/texto");
    }

    @Test
    void seguridadSeEvaluaAntesDeLosErroresDeNegocioYCuerpo() throws Exception {
        mvc.perform(delete("/api/mascotas/delete/999999").with(csrf()))
                .andExpect(status().isUnauthorized()).andExpect(header().exists("WWW-Authenticate"));
        mvc.perform(delete("/api/mascotas/delete/999999").with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/mascotas").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/mascotas").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/h2-console/").with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isForbidden());
    }

    private ResultActions problem(ResultActions result, int status, String path) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(status))
                .andExpect(jsonPath("$.title").isNotEmpty())
                .andExpect(jsonPath("$.detail").isNotEmpty())
                .andExpect(jsonPath("$.instance").value(path))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
        assertThat(result.andReturn().getResponse().getContentAsString())
                .doesNotContain("SQL secreto", "fecha secreta", "-987654321", "java.lang", "com.protectora",
                        "org.hibernate", "Exception", "stackTrace", "rejectedValue");
        return result;
    }
}
