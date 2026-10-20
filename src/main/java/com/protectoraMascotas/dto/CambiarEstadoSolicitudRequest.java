package com.protectoraMascotas.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record CambiarEstadoSolicitudRequest(@NotNull NuevoEstado estado) {

    public enum NuevoEstado {
        APROBADA, RECHAZADA;

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public static NuevoEstado fromJson(Object value) {
            if (value instanceof String name) {
                return valueOf(name);
            }
            // Impedir, por ejemplo, que el ordinal numérico 0 se convierta en APROBADA.
            throw new IllegalArgumentException("El estado debe ser APROBADA o RECHAZADA");
        }
    }

    // Este contrato solo acepta estado; nunca datos personales ni otros campos editables.
    @JsonAnySetter
    public void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("El cuerpo solo admite el campo estado");
    }
}
