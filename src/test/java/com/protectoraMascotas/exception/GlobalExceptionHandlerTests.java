package com.protectoraMascotas.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest());

    @Test
    void mascotaNoEncontradaSeTraduceA404() {
        var response = handler.handleMascotaNoEncontrada(new MascotaNoEncontradaException(90L), request);
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        ProblemDetail body = (ProblemDetail) response.getBody();
        assertThat(body.getStatus()).isEqualTo(404);
        assertThat(body.getType()).isEqualTo(java.net.URI.create("about:blank"));
        assertThat(body.getTitle()).isEqualTo("Mascota no encontrada");
        assertThat(body.getDetail()).isEqualTo("No existe una mascota con ID 90.");
    }

    @Test
    void paginaInvalidaSeTraduceA400() {
        var response = handler.handlePaginaInvalida(new PaginaInvalidaException(), request);
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(((ProblemDetail) response.getBody()).getDetail())
                .isEqualTo("El número de página debe estar entre 0 y 429496729.");
    }

    @Test
    void falloDePersistenciaInesperadoNoSeTransformaEnErrorDelCliente() {
        var failure = new DataIntegrityViolationException("SQL interno y secreto");
        assertThatThrownBy(() -> handler.handleException(failure, request)).isSameAs(failure);
    }

    @Test
    void errorDeProgramacionNoSeTransformaEnErrorDelCliente() {
        var failure = new IllegalArgumentException("Error interno");
        assertThatThrownBy(() -> handler.handleException(failure, request)).isSameAs(failure);
    }
}
