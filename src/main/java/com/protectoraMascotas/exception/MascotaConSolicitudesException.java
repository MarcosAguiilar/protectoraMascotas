package com.protectoraMascotas.exception;

public class MascotaConSolicitudesException extends RuntimeException {

    public MascotaConSolicitudesException() {
        super("No se puede eliminar la mascota porque tiene solicitudes de adopción asociadas.");
    }
}
