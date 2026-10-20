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
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CrearProtectoraRequestTests {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() { factory = Validation.buildDefaultValidatorFactory(); validator = factory.getValidator(); }

    @AfterAll
    static void close() { factory.close(); }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void nombreObligatorio(String nombre) {
        assertThat(validator.validate(new CrearProtectoraRequest(nombre, null, null, null)))
                .anySatisfy(error -> assertThat(error.getPropertyPath().toString()).isEqualTo("nombre"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"sin-arroba", "a@", "dato privado"})
    void emailInvalido(String email) {
        assertThat(validator.validate(new CrearProtectoraRequest("Refugio", email, null, null)))
                .anySatisfy(error -> assertThat(error.getPropertyPath().toString()).isEqualTo("email"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"contacto@example.org"})
    void emailOpcional(String email) {
        assertThat(validator.validate(new CrearProtectoraRequest("Refugio", email, null, null))).isEmpty();
    }

    @ParameterizedTest
    @MethodSource("excesosDeLongitud")
    void camposRespetanLimites(CrearProtectoraRequest request, String field) {
        assertThat(validator.validate(request)).anySatisfy(error -> {
            assertThat(error.getPropertyPath().toString()).isEqualTo(field);
            assertThat(error.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()).isEqualTo("Size");
        });
    }

    static Stream<Arguments> excesosDeLongitud() {
        return Stream.of(
                Arguments.of(new CrearProtectoraRequest("N".repeat(151), null, null, null), "nombre"),
                Arguments.of(new CrearProtectoraRequest("N", "a".repeat(243) + "@example.org", null, null), "email"),
                Arguments.of(new CrearProtectoraRequest("N", null, "1".repeat(31), null), "telefono"),
                Arguments.of(new CrearProtectoraRequest("N", null, null, "M".repeat(151)), "municipio"));
    }

    @Test
    void limitesExactosSonValidos() {
        String email = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(61);
        assertThat(email).hasSize(254);
        assertThat(validator.validate(new CrearProtectoraRequest("N".repeat(150), email, "1".repeat(30), "M".repeat(150))))
                .isEmpty();
    }
}
