package com.protectoraMascotas.data;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import com.protectoraMascotas.entity.Mascota;

public interface MascotaRepository extends JpaRepository<Mascota, Long> {

    @Override
    @EntityGraph(attributePaths = "protectora")
    Optional<Mascota> findById(Long id);

    @Override
    @EntityGraph(attributePaths = "protectora")
    List<Mascota> findAll();

    @Override
    @EntityGraph(attributePaths = "protectora")
    Page<Mascota> findAll(Pageable pageable);

	@EntityGraph(attributePaths = "protectora")
	public List<Mascota> findByNombre(String name);
	
	@EntityGraph(attributePaths = "protectora")
	public Page<Mascota> findAllByOrderByFechaNacDesc(Pageable pageable);

    @EntityGraph(attributePaths = "protectora")
    List<Mascota> findByProtectoraId(Long protectoraId);
	
}
