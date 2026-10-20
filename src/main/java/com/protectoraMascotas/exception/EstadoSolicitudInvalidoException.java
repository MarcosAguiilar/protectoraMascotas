package com.protectoraMascotas.exception;

public class EstadoSolicitudInvalidoException extends RuntimeException {
    public EstadoSolicitudInvalidoException() {
        super("El estado debe ser APROBADA o RECHAZADA.");
    }
}
