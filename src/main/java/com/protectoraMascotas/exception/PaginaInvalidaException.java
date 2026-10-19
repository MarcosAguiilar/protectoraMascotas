package com.protectoraMascotas.exception;

public class PaginaInvalidaException extends RuntimeException {

    public PaginaInvalidaException() {
        super("El número de página debe estar entre 0 y 429496729.");
    }
}
