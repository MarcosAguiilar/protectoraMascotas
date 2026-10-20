package com.protectoraMascotas.data;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.protectoraMascotas.entity.SolicitudAdopcion;
import com.protectoraMascotas.entity.EstadoSolicitud;

public interface SolicitudAdopcionRepository extends JpaRepository<SolicitudAdopcion, Long> {

    boolean existsByMascotaId(Long mascotaId);

    boolean existsByMascotaIdAndEstado(Long mascotaId, EstadoSolicitud estado);

    // Obtener solo el ID evita cargar un estado que pueda quedar obsoleto antes del bloqueo.
    @Query("select s.mascota.id from SolicitudAdopcion s where s.id = :id")
    Optional<Long> findMascotaIdById(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = "mascota")
    Optional<SolicitudAdopcion> findById(Long id);

    @Override
    @EntityGraph(attributePaths = "mascota")
    Page<SolicitudAdopcion> findAll(Pageable pageable);
}
