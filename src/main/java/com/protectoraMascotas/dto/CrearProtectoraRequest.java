package com.protectoraMascotas.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonIgnoreProperties("id")
public record CrearProtectoraRequest(
        @NotBlank(message = "El nombre es obligatorio y no puede contener solo espacios")
        @Size(max = 150) String nombre,
        @Email @Size(max = 254) String email,
        @Size(max = 30) String telefono,
        @Size(max = 150) String municipio) {
}
