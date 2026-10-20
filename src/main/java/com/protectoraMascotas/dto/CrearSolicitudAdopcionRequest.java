package com.protectoraMascotas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties({"id", "fechaSolicitud", "estado"})
public record CrearSolicitudAdopcionRequest(
        @NotNull @Positive Long mascotaId,
        @NotBlank @Size(max = 150) String nombreSolicitante,
        @NotBlank @Email @Size(max = 254) String emailSolicitante) {
}
