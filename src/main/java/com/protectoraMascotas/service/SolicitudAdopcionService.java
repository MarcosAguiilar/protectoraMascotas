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
        Mascota mascota = mascotaRepository.findById(mascotaId)
                .orElseThrow(() -> new MascotaNoEncontradaException(mascotaId));
        if (mascota.getProtectora() == null) {
            throw new MascotaSinProtectoraException();
        }
        solicitud.setId(null);
        solicitud.setMascota(mascota);
        solicitud.setFechaSolicitud(Instant.now().truncatedTo(ChronoUnit.MICROS));
        solicitud.setEstado(EstadoSolicitud.PENDIENTE);
        return repository.save(solicitud);
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
