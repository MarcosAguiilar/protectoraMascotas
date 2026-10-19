package com.protectoraMascotas.data;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;

import com.protectoraMascotas.entity.Mascota;

public interface MascotaRepository extends JpaRepository<Mascota, Long> {

	public List<Mascota> findByNombre(String name);
	
	public Page<Mascota> findAllByOrderByFechaNacDesc(Pageable pageable);
	
}
