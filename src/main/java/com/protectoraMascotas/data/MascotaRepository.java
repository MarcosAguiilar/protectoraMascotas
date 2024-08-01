package com.protectoraMascotas.data;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.repository.PagingAndSortingRepository;

import com.protectoraMascotas.entity.Mascota;

public interface MascotaRepository extends PagingAndSortingRepository<Mascota, Long> {

	public List<Mascota> findByNombre(String name);
	
	public Page<Mascota> findAllByOrderByFechaNacDesc(Pageable pageable);
	
}
