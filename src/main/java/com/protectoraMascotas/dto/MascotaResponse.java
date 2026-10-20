package com.protectoraMascotas.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

public record MascotaResponse(
        Long id,
        String nombre,
        Date fechaNac,
        String raza,
        Long peso,
        @JsonProperty("tiene_chip") boolean tieneChip,
        @JsonProperty("url_foto") String urlFoto,
        @Schema(description = "ID de la protectora; se omite cuando la mascota no tiene protectora")
        @JsonInclude(JsonInclude.Include.NON_NULL) Long protectoraId) {
}
