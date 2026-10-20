package com.protectoraMascotas.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.protectoraMascotas.dto.CrearProtectoraRequest;
import com.protectoraMascotas.dto.ProtectoraResponse;
import com.protectoraMascotas.entity.Protectora;

class ProtectoraMapperTests {

    private final ProtectoraMapper mapper = new ProtectoraMapper();

    @Test
    void entradaConstruyeEntidadNuevaSinIdYConTodosLosCampos() {
        var request = new CrearProtectoraRequest(" Refugio ", "contacto@example.org", "+34 900 000 000", "Madrid");
        Protectora entity = mapper.toEntity(request);
        assertThat(entity.getId()).isNull();
        assertThat(entity.getNombre()).isEqualTo(request.nombre());
        assertThat(entity.getEmail()).isEqualTo(request.email());
        assertThat(entity.getTelefono()).isEqualTo(request.telefono());
        assertThat(entity.getMunicipio()).isEqualTo(request.municipio());
    }

    @Test
    void salidaConservaIdYTodosLosCamposSinExponerLaEntidad() {
        Protectora entity = mapper.toEntity(new CrearProtectoraRequest("Refugio", "hola@example.org", "123", "Sevilla"));
        entity.setId(7L);
        assertThat(mapper.toResponse(entity))
                .isEqualTo(new ProtectoraResponse(7L, "Refugio", "hola@example.org", "123", "Sevilla"));
    }

    @Test
    void camposOpcionalesNulosSeConservan() {
        Protectora entity = mapper.toEntity(new CrearProtectoraRequest("Refugio", null, null, null));
        assertThat(mapper.toResponse(entity)).isEqualTo(new ProtectoraResponse(null, "Refugio", null, null, null));
    }
}
