package com.protectoraMascotas.dto;

import java.time.Instant;

import com.protectoraMascotas.entity.EstadoSolicitud;

public record SolicitudAdopcionResponse(Long id, Long mascotaId, String nombreSolicitante,
        String emailSolicitante, Instant fechaSolicitud, EstadoSolicitud estado) {
}
