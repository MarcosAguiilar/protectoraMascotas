package com.protectoraMascotas.service;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.protectoraMascotas.data.MascotaRepository;
import com.protectoraMascotas.entity.Mascota;

@Service
@Transactional(readOnly = true)
public class MascotaService {

    private final MascotaRepository mascotaRepository;

    public MascotaService(MascotaRepository mascotaRepository) {
        this.mascotaRepository = mascotaRepository;
    }

    public Optional<Mascota> findById(Long id) {
        return mascotaRepository.findById(id);
    }

    public List<Mascota> findByNombre(String name) {
        return mascotaRepository.findByNombre(name);
    }

    @Transactional
    public Mascota save(Mascota mascota) {
        return mascotaRepository.save(mascota);
    }

    public List<Mascota> findAll() {
        return mascotaRepository.findAll();
    }

    public List<Mascota> findTwentyRecent() {
        return mascotaRepository.findAllByOrderByFechaNacDesc(PageRequest.of(0, 20)).getContent();
    }

    public Page<Mascota> findAllByPage(Integer page) {
        return mascotaRepository.findAll(PageRequest.of(page, 5));
    }

    @Transactional
    public void deleteById(Long id) {
        mascotaRepository.deleteById(id);
    }
}
