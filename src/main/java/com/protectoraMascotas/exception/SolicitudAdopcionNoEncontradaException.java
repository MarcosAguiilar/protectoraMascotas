package com.protectoraMascotas.exception;

public class SolicitudAdopcionNoEncontradaException extends RuntimeException {

    public SolicitudAdopcionNoEncontradaException(Long id) {
        super("No existe una solicitud de adopción con ID " + id + ".");
    }
}
