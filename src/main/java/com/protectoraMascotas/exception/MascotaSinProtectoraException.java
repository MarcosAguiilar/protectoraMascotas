package com.protectoraMascotas.exception;

public class MascotaSinProtectoraException extends RuntimeException {

    public MascotaSinProtectoraException() {
        super("No se puede registrar la solicitud: la mascota no tiene una protectora asignada.");
    }
}
