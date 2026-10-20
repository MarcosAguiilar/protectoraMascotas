package com.protectoraMascotas;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.protectoraMascotas.data.MascotaRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PositiveValidationHttpTests extends SecurityTestSupport {

    @LocalServerPort private int port;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private MascotaRepository repository;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String authorization = "Basic " + Base64.getEncoder()
            .encodeToString((USERNAME + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8));
    private String cookie;
    private String csrfHeader;
    private String csrfToken;

    @BeforeEach
    void obtainRealCsrfTokenAndSession() throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri("/api/csrf"))
                .timeout(Duration.ofSeconds(10)).header("Authorization", authorization).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode token = jsonMapper.readTree(response.body());
        csrfHeader = token.get("headerName").asText();
        csrfToken = token.get("token").asText();
        // El perfil prod marca la cookie Secure. El cliente de pruebas reenvía explícitamente
        // esa misma sesión por HTTP local, sin cambiar la configuración de seguridad.
        cookie = response.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("JSESSIONID="))
                .map(value -> value.split(";", 2)[0]).findFirst().orElseThrow();
    }

    @ParameterizedTest
    @CsvSource({
            "peso,0,El peso debe ser positivo",
            "peso,-987654321,El peso debe ser positivo",
            "protectoraId,0,El ID de la protectora debe ser positivo",
            "protectoraId,-123456789,El ID de la protectora debe ser positivo"
    })
    void positiveDevuelveElMensajeDelCampoSinExponerDatos(String field, long value, String message) throws Exception {
        JsonNode problem = postInvalid("{\"nombre\":\"Validación\",\"" + field + "\":" + value + "}");
        assertThat(problem.get("errors").size()).isEqualTo(1);
        assertThat(problem.get("errors").get(0).get("field").asText()).isEqualTo(field);
        assertThat(problem.get("errors").get(0).get("message").asText()).isEqualTo(message);
    }

    @Test
    void ambosCamposInvalidosConservanSuPropioMensaje() throws Exception {
        JsonNode problem = postInvalid("{\"nombre\":\"Validación\",\"peso\":-987654321,\"protectoraId\":-123456789}");
        assertThat(problem.get("errors").size()).isEqualTo(2);
        java.util.Map<String, String> messages = new java.util.HashMap<>();
        problem.get("errors").forEach(error -> messages.put(error.get("field").asText(), error.get("message").asText()));
        assertThat(messages).containsExactlyInAnyOrderEntriesOf(java.util.Map.of(
                "peso", "El peso debe ser positivo", "protectoraId", "El ID de la protectora debe ser positivo"));
    }

    private JsonNode postInvalid(String json) throws Exception {
        long before = repository.count();
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri("/api/mascotas"))
                .timeout(Duration.ofSeconds(10)).header("Authorization", authorization)
                .header("Cookie", cookie).header(csrfHeader, csrfToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/problem+json");
        JsonNode problem = jsonMapper.readTree(response.body());
        assertThat(problem.get("type").asText()).isEqualTo("about:blank");
        assertThat(problem.get("title").asText()).isEqualTo("Datos no válidos");
        assertThat(problem.get("status").asInt()).isEqualTo(400);
        assertThat(problem.get("detail").asText()).isEqualTo("Hay datos de entrada no válidos.");
        assertThat(problem.get("instance").asText()).isEqualTo("/api/mascotas");
        assertThat(response.body()).doesNotContain("-987654321", "-123456789", "rejectedValue",
                "exception", "trace", "java.lang", "org.hibernate", "com.protectoraMascotas");
        assertThat(problem.size()).isEqualTo(6);
        problem.get("errors").forEach(error -> assertThat(error.size()).isEqualTo(2));
        assertThat(repository.count()).isEqualTo(before);
        return problem;
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
