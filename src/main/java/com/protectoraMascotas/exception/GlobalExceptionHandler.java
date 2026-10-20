package com.protectoraMascotas.exception;

import java.net.URI;

import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(MascotaNoEncontradaException.class)
    public ResponseEntity<Object> handleMascotaNoEncontrada(MascotaNoEncontradaException ex,
            WebRequest request) {
        return problem(ex, HttpStatus.NOT_FOUND, "Mascota no encontrada", ex.getMessage(), request);
    }

    @ExceptionHandler(PaginaInvalidaException.class)
    public ResponseEntity<Object> handlePaginaInvalida(PaginaInvalidaException ex, WebRequest request) {
        return problem(ex, HttpStatus.BAD_REQUEST, "Página no válida", ex.getMessage(), request);
    }

    @ExceptionHandler(ProtectoraNoEncontradaException.class)
    public ResponseEntity<Object> handleProtectoraNoEncontrada(ProtectoraNoEncontradaException ex,
            WebRequest request) {
        return problem(ex, HttpStatus.NOT_FOUND, "Protectora no encontrada", ex.getMessage(), request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status,
                "Hay datos de entrada no válidos.");
        body.setTitle("Datos no válidos");
        // Solo mensajes de las restricciones declaradas; nunca valores rechazados ni mensajes internos.
        body.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), switch (error.getCode()) {
                    case "NotBlank" -> "El nombre es obligatorio y no puede contener solo espacios";
                    case "Positive" -> switch (error.getField()) {
                        case "peso" -> "El peso debe ser positivo";
                        case "protectoraId" -> "El ID de la protectora debe ser positivo";
                        default -> "El valor debe ser positivo";
                    };
                    case "Email" -> "El email debe tener un formato válido";
                    case "Size" -> "La longitud del campo supera el máximo permitido";
                    default -> "El valor no es válido";
                })).toList());
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problem(ex, status, "JSON no válido",
                "El cuerpo debe contener un JSON válido con los tipos de datos esperados.", headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return problem(ex, status, "Parámetro no válido",
                "Los identificadores y el número de página deben ser números enteros válidos.", headers, request);
    }

    private ResponseEntity<Object> problem(Exception ex, HttpStatusCode status, String title,
            String detail, WebRequest request) {
        return problem(ex, status, title, detail, new HttpHeaders(), request);
    }

    private ResponseEntity<Object> problem(Exception ex, HttpStatusCode status, String title,
            String detail, HttpHeaders headers, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
        body.setTitle(title);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.putAll(headers);
        if (body instanceof ProblemDetail problemDetail) {
            if (problemDetail.getType() == null) {
                problemDetail.setType(URI.create("about:blank"));
            }
            responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        }
        return super.createResponseEntity(body, responseHeaders, status, request);
    }

    public record FieldError(String field, String message) {}
}
