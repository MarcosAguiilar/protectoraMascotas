package com.protectoraMascotas.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MascotaResponse(
        Long id,
        String nombre,
        Date fechaNac,
        String raza,
        Long peso,
        @JsonProperty("tiene_chip") boolean tieneChip,
        @JsonProperty("url_foto") String urlFoto) {
}
