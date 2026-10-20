package com.protectoraMascotas.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.media.Schema;

// El ID es responsabilidad del servidor, incluso si un cliente lo envia en el JSON.
@JsonIgnoreProperties("id")
public record CrearMascotaRequest(
        @NotBlank(message = "El nombre es obligatorio y no puede contener solo espacios") String nombre,
        Date fechaNac,
        String raza,
        @Positive(message = "El peso debe ser positivo") Long peso,
        @JsonProperty("tiene_chip") boolean tieneChip,
        @JsonProperty("url_foto") String urlFoto,
        @Schema(description = "ID opcional de una protectora existente; debe ser positivo")
        @Positive(message = "El ID de la protectora debe ser positivo") Long protectoraId) {
}
