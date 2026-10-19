package com.protectoraMascotas.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class CrearMascotaRequestTests {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void nombreDebeContenerTexto(String nombre) {
        Set<ConstraintViolation<CrearMascotaRequest>> violations = validator.validate(request(nombre, null));

        assertThat(violations).singleElement()
                .satisfies(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("nombre"));
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MIN_VALUE})
    void pesoNoPositivoEsInvalido(Long peso) {
        Set<ConstraintViolation<CrearMascotaRequest>> violations = validator.validate(request("Luna", peso));

        assertThat(violations).singleElement()
                .satisfies(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo("peso"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {1, Long.MAX_VALUE})
    void pesoEsOpcionalYSoloExigeSerPositivo(Long peso) {
        CrearMascotaRequest request = request(" Luna ", peso);

        assertThat(validator.validate(request)).isEmpty();
        assertThat(request.nombre()).isEqualTo(" Luna ");
    }

    private static CrearMascotaRequest request(String nombre, Long peso) {
        return new CrearMascotaRequest(nombre, null, null, peso, false, null);
    }
}
