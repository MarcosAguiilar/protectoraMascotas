package com.protectoraMascotas.data;

import org.springframework.data.jpa.repository.JpaRepository;

import com.protectoraMascotas.entity.Protectora;

public interface ProtectoraRepository extends JpaRepository<Protectora, Long> {
}
