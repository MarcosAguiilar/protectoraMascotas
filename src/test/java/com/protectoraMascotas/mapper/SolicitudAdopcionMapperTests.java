package com.protectoraMascotas.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.protectoraMascotas.dto.CrearSolicitudAdopcionRequest;
import com.protectoraMascotas.entity.EstadoSolicitud;
import com.protectoraMascotas.entity.Mascota;
import com.protectoraMascotas.entity.SolicitudAdopcion;

class SolicitudAdopcionMapperTests {

    private final SolicitudAdopcionMapper mapper = new SolicitudAdopcionMapper();

    @Test
    void entradaSoloCopiaNombreYEmail() {
        var request = new CrearSolicitudAdopcionRequest(8L, "Persona de prueba", "prueba@example.org");
        SolicitudAdopcion entity = mapper.toEntity(request);
        assertThat(entity.getNombreSolicitante()).isEqualTo(request.nombreSolicitante());
        assertThat(entity.getEmailSolicitante()).isEqualTo(request.emailSolicitante());
        assertThat(entity.getId()).isNull();
        assertThat(entity.getMascota()).isNull();
        assertThat(entity.getFechaSolicitud()).isNull();
        assertThat(entity.getEstado()).isNull();
    }

    @Test
    void salidaExponeIdDeMascotaYSoloCamposDelContrato() {
        SolicitudAdopcion entity = solicitud();
        var response = mapper.toResponse(entity);
        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.mascotaId()).isEqualTo(8L);
        assertThat(response.nombreSolicitante()).isEqualTo(entity.getNombreSolicitante());
        assertThat(response.emailSolicitante()).isEqualTo(entity.getEmailSolicitante());
        assertThat(response.fechaSolicitud()).isEqualTo(entity.getFechaSolicitud());
        assertThat(response.estado()).isEqualTo(EstadoSolicitud.PENDIENTE);
    }

    @Test
    void paginaConservaMetadatosSinExponerPageImpl() {
        var page = new PageImpl<>(List.of(solicitud()), PageRequest.of(1, 2), 3);
        var result = mapper.toPage(page);
        assertThat(result.content()).containsExactly(mapper.toResponse(page.getContent().get(0)));
        assertThat(result.number()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.first()).isFalse();
        assertThat(result.last()).isTrue();
    }

    private SolicitudAdopcion solicitud() {
        SolicitudAdopcion entity = mapper.toEntity(new CrearSolicitudAdopcionRequest(8L, "Prueba", "prueba@example.org"));
        entity.setId(2L);
        Mascota mascota = new Mascota();
        mascota.setId(8L);
        entity.setMascota(mascota);
        entity.setFechaSolicitud(Instant.parse("2026-01-01T12:00:00Z"));
        entity.setEstado(EstadoSolicitud.PENDIENTE);
        return entity;
    }
}
