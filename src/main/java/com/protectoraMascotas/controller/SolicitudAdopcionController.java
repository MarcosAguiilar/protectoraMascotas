package com.protectoraMascotas.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.protectoraMascotas.dto.CrearSolicitudAdopcionRequest;
import com.protectoraMascotas.dto.CambiarEstadoSolicitudRequest;
import com.protectoraMascotas.entity.EstadoSolicitud;
import com.protectoraMascotas.dto.PaginaSolicitudesAdopcionResponse;
import com.protectoraMascotas.dto.SolicitudAdopcionResponse;
import com.protectoraMascotas.mapper.SolicitudAdopcionMapper;
import com.protectoraMascotas.service.SolicitudAdopcionService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(path = "/api/solicitudes-adopcion", produces = "application/json")
@SecurityRequirement(name = "basicAuth")
@Tag(name = "Solicitudes de adopción", description = "Datos personales: todas las operaciones requieren autenticación")
@ApiResponse(responseCode = "401", description = "Falta autenticación válida", content = @Content)
public class SolicitudAdopcionController {

    private final SolicitudAdopcionService service;
    private final SolicitudAdopcionMapper mapper;

    public SolicitudAdopcionController(SolicitudAdopcionService service, SolicitudAdopcionMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping(consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar una solicitud de adopción",
            description = "El servidor genera ID, fecha UTC y estado PENDIENTE; los valores enviados para esos campos se ignoran")
    @Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER, required = true,
            description = "Token de GET /api/csrf; conservar la misma cookie de sesión")
    @ApiResponse(responseCode = "201", description = "Solicitud creada", content = @Content(
            mediaType = "application/json", schema = @Schema(implementation = SolicitudAdopcionResponse.class)))
    @ApiResponse(responseCode = "400", description = "JSON o datos de entrada inválidos", content = @Content(
            mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Mascota inexistente", content = @Content(
            mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Mascota sin protectora asignada o con solicitud ya aprobada", content = @Content(
            mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "CSRF ausente o inválido", content = @Content)
    public SolicitudAdopcionResponse save(@Valid @RequestBody CrearSolicitudAdopcionRequest request) {
        return mapper.toResponse(service.save(mapper.toEntity(request), request.mascotaId()));
    }

    @PatchMapping(path = "/{id}/estado", consumes = "application/json")
    @Operation(summary = "Aprobar o rechazar una solicitud pendiente",
            description = "APROBADA y RECHAZADA son finales. Como máximo una solicitud aprobada por mascota; no se modifican las demás pendientes.")
    @Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER, required = true,
            description = "Token de GET /api/csrf; conservar la misma cookie de sesión")
    @ApiResponse(responseCode = "200", description = "Estado actualizado", content = @Content(
            mediaType = "application/json", schema = @Schema(implementation = SolicitudAdopcionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Solo se admite estado APROBADA o RECHAZADA", content = @Content(
            mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "404", description = "Solicitud inexistente", content = @Content(
            mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "409", description = "Estado final o mascota con otra solicitud aprobada", content = @Content(
            mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "403", description = "CSRF ausente o inválido", content = @Content)
    public SolicitudAdopcionResponse cambiarEstado(@PathVariable("id") Long id,
            @Valid @RequestBody CambiarEstadoSolicitudRequest request) {
        return mapper.toResponse(service.cambiarEstado(id, EstadoSolicitud.valueOf(request.estado().name())));
    }

    @GetMapping({"/{id}", "/{id}/"})
    @Operation(summary = "Consultar una solicitud por ID", description = "Consulta autenticada; contiene datos personales")
    @ApiResponse(responseCode = "200", description = "Solicitud encontrada", content = @Content(
            mediaType = "application/json", schema = @Schema(implementation = SolicitudAdopcionResponse.class)))
    @ApiResponse(responseCode = "404", description = "Solicitud inexistente", content = @Content(
            mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "400", description = "ID no convertible a Long", content = @Content(
            mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public SolicitudAdopcionResponse findById(@PathVariable("id") Long id) {
        return mapper.toResponse(service.findById(id));
    }

    @GetMapping({"", "/"})
    @Operation(summary = "Listar solicitudes con paginación", description = "Consulta autenticada; orden fijo por ID ascendente")
    @ApiResponse(responseCode = "200", description = "Página de solicitudes", content = @Content(
            mediaType = "application/json", schema = @Schema(implementation = PaginaSolicitudesAdopcionResponse.class)))
    @ApiResponse(responseCode = "400", description = "Página o tamaño inválidos", content = @Content(
            mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public PaginaSolicitudesAdopcionResponse findAll(
            @Parameter(description = "Índice desde cero", schema = @Schema(minimum = "0"))
            @RequestParam(name = "page", defaultValue = "0") int page,
            @Parameter(description = "Tamaño entre 1 y 100", schema = @Schema(minimum = "1", maximum = "100"))
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return mapper.toPage(service.findAll(page, size));
    }
}
