package com.protectoraMascotas.mapper;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.protectoraMascotas.dto.CrearSolicitudAdopcionRequest;
import com.protectoraMascotas.dto.PaginaSolicitudesAdopcionResponse;
import com.protectoraMascotas.dto.SolicitudAdopcionResponse;
import com.protectoraMascotas.entity.SolicitudAdopcion;

@Component
public class SolicitudAdopcionMapper {

    public SolicitudAdopcion toEntity(CrearSolicitudAdopcionRequest request) {
        SolicitudAdopcion solicitud = new SolicitudAdopcion();
        solicitud.setNombreSolicitante(request.nombreSolicitante());
        solicitud.setEmailSolicitante(request.emailSolicitante());
        return solicitud;
    }

    public SolicitudAdopcionResponse toResponse(SolicitudAdopcion solicitud) {
        return new SolicitudAdopcionResponse(solicitud.getId(), solicitud.getMascota().getId(),
                solicitud.getNombreSolicitante(), solicitud.getEmailSolicitante(),
                solicitud.getFechaSolicitud(), solicitud.getEstado());
    }

    public PaginaSolicitudesAdopcionResponse toPage(Page<SolicitudAdopcion> page) {
        return new PaginaSolicitudesAdopcionResponse(page.getContent().stream().map(this::toResponse).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(),
                page.isFirst(), page.isLast());
    }
}
