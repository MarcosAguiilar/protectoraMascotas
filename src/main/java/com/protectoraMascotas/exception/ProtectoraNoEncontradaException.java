package com.protectoraMascotas.exception;

public class ProtectoraNoEncontradaException extends RuntimeException {

    public ProtectoraNoEncontradaException(Long id) {
        super("No existe una protectora con ID " + id + ".");
    }
}
