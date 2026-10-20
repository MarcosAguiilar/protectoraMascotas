package com.protectoraMascotas.exception;

public class PaginacionSolicitudInvalidaException extends RuntimeException {

    public PaginacionSolicitudInvalidaException() {
        super("La página debe ser mayor o igual que cero, el tamaño entre 1 y 100 y el desplazamiento no debe superar el rango entero admitido.");
    }
}
