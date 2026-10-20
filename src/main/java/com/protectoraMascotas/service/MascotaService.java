package com.protectoraMascotas.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.protectoraMascotas.data.MascotaRepository;
import com.protectoraMascotas.data.ProtectoraRepository;
import com.protectoraMascotas.entity.Mascota;
import com.protectoraMascotas.exception.MascotaNoEncontradaException;
import com.protectoraMascotas.exception.PaginaInvalidaException;
import com.protectoraMascotas.exception.ProtectoraNoEncontradaException;

@Service
@Transactional(readOnly = true)
public class MascotaService {

    private final MascotaRepository mascotaRepository;
    private final ProtectoraRepository protectoraRepository;

    public MascotaService(MascotaRepository mascotaRepository, ProtectoraRepository protectoraRepository) {
        this.mascotaRepository = mascotaRepository;
        this.protectoraRepository = protectoraRepository;
    }

    public Mascota findById(Long id) {
        return mascotaRepository.findById(id)
                .orElseThrow(() -> new MascotaNoEncontradaException(id));
    }

    public List<Mascota> findByNombre(String name) {
        return mascotaRepository.findByNombre(name);
    }

    @Transactional
    public Mascota save(Mascota mascota, Long protectoraId) {
        mascota.setProtectora(protectoraId == null ? null : protectoraRepository.findById(protectoraId)
                .orElseThrow(() -> new ProtectoraNoEncontradaException(protectoraId)));
        return mascotaRepository.save(mascota);
    }

    public List<Mascota> findByProtectoraId(Long protectoraId) {
        if (!protectoraRepository.existsById(protectoraId)) {
            throw new ProtectoraNoEncontradaException(protectoraId);
        }
        return mascotaRepository.findByProtectoraId(protectoraId);
    }

    public List<Mascota> findAll() {
        return mascotaRepository.findAll();
    }

    public List<Mascota> findTwentyRecent() {
        return mascotaRepository.findAllByOrderByFechaNacDesc(PageRequest.of(0, 20)).getContent();
    }

    public Page<Mascota> findAllByPage(Integer page) {
        // JPA admite offsets int; cinco elementos por página no deben desbordarlo.
        if (page == null || page < 0 || page > Integer.MAX_VALUE / 5) {
            throw new PaginaInvalidaException();
        }
        return mascotaRepository.findAll(PageRequest.of(page, 5));
    }

    @Transactional
    public void deleteById(Long id) {
        Mascota mascota = findById(id);
        mascotaRepository.delete(mascota);
    }
}
