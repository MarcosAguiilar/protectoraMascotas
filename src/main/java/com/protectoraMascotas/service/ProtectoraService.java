package com.protectoraMascotas.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.protectoraMascotas.data.ProtectoraRepository;
import com.protectoraMascotas.entity.Protectora;
import com.protectoraMascotas.exception.ProtectoraNoEncontradaException;

@Service
@Transactional(readOnly = true)
public class ProtectoraService {

    private final ProtectoraRepository repository;

    public ProtectoraService(ProtectoraRepository repository) {
        this.repository = repository;
    }

    public List<Protectora> findAll() {
        return repository.findAll();
    }

    public Protectora findById(Long id) {
        return repository.findById(id).orElseThrow(() -> new ProtectoraNoEncontradaException(id));
    }

    @Transactional
    public Protectora save(Protectora protectora) {
        return repository.save(protectora);
    }
}
