package com.protectoraMascotas.data;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import com.protectoraMascotas.entity.Mascota;

public interface MascotaRepository extends JpaRepository<Mascota, Long> {

    // Spring Data 3 ignora IDs inexistentes; conservar la excepcion de Spring Data 2.
    @Override
    @Transactional
    default void deleteById(Long id) {
        Assert.notNull(id, "The given id must not be null!");
        Mascota mascota = findById(id).orElseThrow(() -> new EmptyResultDataAccessException(
                String.format("No %s entity with id %s exists!", Mascota.class, id), 1));
        delete(mascota);
    }

	public List<Mascota> findByNombre(String name);
	
	public Page<Mascota> findAllByOrderByFechaNacDesc(Pageable pageable);
	
}
