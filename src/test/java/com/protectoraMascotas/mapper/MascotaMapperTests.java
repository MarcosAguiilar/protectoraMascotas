package com.protectoraMascotas.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Date;

import org.junit.jupiter.api.Test;

import com.protectoraMascotas.dto.CrearMascotaRequest;
import com.protectoraMascotas.dto.MascotaResponse;
import com.protectoraMascotas.entity.Mascota;

class MascotaMapperTests {

    private final MascotaMapper mapper = new MascotaMapper();

    @Test
    void peticionSeConvierteEnEntidadNuevaSinId() {
        Date fecha = Date.from(Instant.parse("2020-05-01T12:00:00Z"));
        CrearMascotaRequest request = new CrearMascotaRequest(" Luna ", fecha, "Mestiza", 12L,
                true, "www.example.com/luna.jpg");

        Mascota entity = mapper.toEntity(request);

        assertThat(entity.getId()).isNull();
        assertThat(entity.getNombre()).isEqualTo(" Luna ");
        assertThat(entity.getFechaNac()).isEqualTo(fecha).isNotSameAs(fecha);
        assertThat(entity.getRaza()).isEqualTo("Mestiza");
        assertThat(entity.getPeso()).isEqualTo(12L);
        assertThat(entity.isTiene_chip()).isTrue();
        assertThat(entity.getUrl_foto()).isEqualTo("www.example.com/luna.jpg");
        fecha.setTime(0);
        assertThat(entity.getFechaNac().toInstant()).isEqualTo(Instant.parse("2020-05-01T12:00:00Z"));
    }

    @Test
    void respuestaConservaTodosLosCamposYSeparaLaFechaMutable() {
        Mascota entity = new Mascota();
        entity.setId(5L);
        entity.setNombre("Luna");
        entity.setFechaNac(Date.from(Instant.parse("2020-05-01T12:00:00Z")));
        entity.setRaza("Mestiza");
        entity.setPeso(12L);
        entity.setTiene_chip(true);
        entity.setUrl_foto("www.example.com/luna.jpg");

        MascotaResponse response = mapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(5L);
        assertThat(response.nombre()).isEqualTo("Luna");
        assertThat(response.fechaNac()).isEqualTo(entity.getFechaNac()).isNotSameAs(entity.getFechaNac());
        assertThat(response.raza()).isEqualTo("Mestiza");
        assertThat(response.peso()).isEqualTo(12L);
        assertThat(response.tieneChip()).isTrue();
        assertThat(response.urlFoto()).isEqualTo("www.example.com/luna.jpg");
        response.fechaNac().setTime(0);
        assertThat(entity.getFechaNac().toInstant()).isEqualTo(Instant.parse("2020-05-01T12:00:00Z"));
    }

    @Test
    void camposOpcionalesNulosYChipFalseSeConservan() {
        Mascota entity = mapper.toEntity(new CrearMascotaRequest("Luna", null, null, null, false, null));
        MascotaResponse response = mapper.toResponse(entity);

        assertThat(response.id()).isNull();
        assertThat(response.nombre()).isEqualTo("Luna");
        assertThat(response.fechaNac()).isNull();
        assertThat(response.raza()).isNull();
        assertThat(response.peso()).isNull();
        assertThat(response.tieneChip()).isFalse();
        assertThat(response.urlFoto()).isNull();
    }
}
