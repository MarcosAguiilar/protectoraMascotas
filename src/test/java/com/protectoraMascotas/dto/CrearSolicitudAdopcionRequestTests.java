package com.protectoraMascotas.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CrearSolicitudAdopcionRequestTests {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() { factory = Validation.buildDefaultValidatorFactory(); validator = factory.getValidator(); }
    @AfterAll
    static void close() { factory.close(); }

    @ParameterizedTest
    @MethodSource("invalidRequests")
    void validaCampos(CrearSolicitudAdopcionRequest request, String field) {
        assertThat(validator.validate(request))
                .anySatisfy(error -> assertThat(error.getPropertyPath().toString()).isEqualTo(field));
    }

    static Stream<Arguments> invalidRequests() {
        return Stream.of(
                Arguments.of(new CrearSolicitudAdopcionRequest(null, "Prueba", "prueba@example.org"), "mascotaId"),
                Arguments.of(new CrearSolicitudAdopcionRequest(0L, "Prueba", "prueba@example.org"), "mascotaId"),
                Arguments.of(new CrearSolicitudAdopcionRequest(-1L, "Prueba", "prueba@example.org"), "mascotaId"),
                Arguments.of(new CrearSolicitudAdopcionRequest(1L, null, "prueba@example.org"), "nombreSolicitante"),
                Arguments.of(new CrearSolicitudAdopcionRequest(1L, "  ", "prueba@example.org"), "nombreSolicitante"),
                Arguments.of(new CrearSolicitudAdopcionRequest(1L, "N".repeat(151), "prueba@example.org"), "nombreSolicitante"),
                Arguments.of(new CrearSolicitudAdopcionRequest(1L, "Prueba", null), "emailSolicitante"),
                Arguments.of(new CrearSolicitudAdopcionRequest(1L, "Prueba", ""), "emailSolicitante"),
                Arguments.of(new CrearSolicitudAdopcionRequest(1L, "Prueba", "sin-formato"), "emailSolicitante"),
                Arguments.of(new CrearSolicitudAdopcionRequest(1L, "Prueba", "e".repeat(255)), "emailSolicitante"));
    }

    @Test
    void solicitudValidaNoRecortaNiNormaliza() {
        var request = new CrearSolicitudAdopcionRequest(1L, " Prueba ", "prueba@example.org");
        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.nombreSolicitante()).isEqualTo(" Prueba ");
    }
}
