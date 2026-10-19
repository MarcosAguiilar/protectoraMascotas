package com.protectoraMascotas.exception;

public class MascotaNoEncontradaException extends RuntimeException {

    public MascotaNoEncontradaException(Long id) {
        super("No existe una mascota con ID " + id + ".");
    }
}
