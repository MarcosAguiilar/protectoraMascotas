package com.protectoraMascotas.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.protectoraMascotas.data.MascotaRepository;
import com.protectoraMascotas.data.SolicitudAdopcionRepository;
import com.protectoraMascotas.entity.EstadoSolicitud;
import com.protectoraMascotas.entity.Mascota;
import com.protectoraMascotas.entity.SolicitudAdopcion;
import com.protectoraMascotas.exception.MascotaNoEncontradaException;
import com.protectoraMascotas.exception.MascotaSinProtectoraException;
import com.protectoraMascotas.exception.PaginacionSolicitudInvalidaException;
import com.protectoraMascotas.exception.SolicitudAdopcionNoEncontradaException;
import com.protectoraMascotas.exception.ConflictoEstadoSolicitudException;
import com.protectoraMascotas.exception.EstadoSolicitudInvalidoException;

@Service
@Transactional(readOnly = true)
public class SolicitudAdopcionService {

    private final SolicitudAdopcionRepository repository;
    private final MascotaRepository mascotaRepository;

    public SolicitudAdopcionService(SolicitudAdopcionRepository repository, MascotaRepository mascotaRepository) {
        this.repository = repository;
        this.mascotaRepository = mascotaRepository;
    }

    @Transactional
    public SolicitudAdopcion save(SolicitudAdopcion solicitud, Long mascotaId) {
        Mascota mascota = mascotaRepository.findByIdForUpdate(mascotaId)
                .orElseThrow(() -> new MascotaNoEncontradaException(mascotaId));
        if (mascota.getProtectora() == null) {
            throw new MascotaSinProtectoraException();
        }
        comprobarSinAprobada(mascotaId);
        solicitud.setId(null);
        solicitud.setMascota(mascota);
        solicitud.setFechaSolicitud(Instant.now().truncatedTo(ChronoUnit.MICROS));
        solicitud.setEstado(EstadoSolicitud.PENDIENTE);
        return repository.save(solicitud);
    }

    @Transactional
    public SolicitudAdopcion cambiarEstado(Long id, EstadoSolicitud nuevoEstado) {
        if (nuevoEstado == null || nuevoEstado == EstadoSolicitud.PENDIENTE) {
            throw new EstadoSolicitudInvalidoException();
        }
        Long mascotaId = repository.findMascotaIdById(id)
                .orElseThrow(() -> new SolicitudAdopcionNoEncontradaException(id));
        // Misma fila y orden de bloqueo para altas y cambios de estado, también entre instancias.
        mascotaRepository.findByIdForUpdate(mascotaId)
                .orElseThrow(() -> new MascotaNoEncontradaException(mascotaId));
        SolicitudAdopcion solicitud = findById(id);
        if (solicitud.getEstado() != EstadoSolicitud.PENDIENTE) {
            throw new ConflictoEstadoSolicitudException("La solicitud ya tiene un estado final y no puede modificarse.");
        }
        if (nuevoEstado == EstadoSolicitud.APROBADA) {
            comprobarSinAprobada(mascotaId);
        }
        solicitud.setEstado(nuevoEstado);
        return solicitud; // Entidad gestionada: Hibernate persiste el cambio al confirmar la transacción.
    }

    private void comprobarSinAprobada(Long mascotaId) {
        if (repository.existsByMascotaIdAndEstado(mascotaId, EstadoSolicitud.APROBADA)) {
            throw new ConflictoEstadoSolicitudException("La mascota ya tiene una solicitud de adopción aprobada.");
        }
    }

    public SolicitudAdopcion findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new SolicitudAdopcionNoEncontradaException(id));
    }

    public Page<SolicitudAdopcion> findAll(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new PaginacionSolicitudInvalidaException();
        }
        return repository.findAll(PageRequest.of(page, size, Sort.by("id").ascending()));
    }
}
