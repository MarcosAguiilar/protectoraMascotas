package com.protectoraMascotas.data;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.protectoraMascotas.entity.SolicitudAdopcion;

public interface SolicitudAdopcionRepository extends JpaRepository<SolicitudAdopcion, Long> {

    @Override
    @EntityGraph(attributePaths = "mascota")
    Optional<SolicitudAdopcion> findById(Long id);

    @Override
    @EntityGraph(attributePaths = "mascota")
    Page<SolicitudAdopcion> findAll(Pageable pageable);
}
