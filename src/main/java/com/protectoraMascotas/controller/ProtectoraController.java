package com.protectoraMascotas.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.protectoraMascotas.dto.CrearProtectoraRequest;
import com.protectoraMascotas.dto.ProtectoraResponse;
import com.protectoraMascotas.dto.MascotaResponse;
import com.protectoraMascotas.mapper.MascotaMapper;
import com.protectoraMascotas.service.MascotaService;
import com.protectoraMascotas.mapper.ProtectoraMapper;
import com.protectoraMascotas.service.ProtectoraService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(path = "/api/protectoras", produces = "application/json")
@Tag(name = "Protectoras", description = "Registro y consulta de protectoras de animales")
public class ProtectoraController {

    private final ProtectoraService service;
    private final ProtectoraMapper mapper;
    private final MascotaService mascotaService;
    private final MascotaMapper mascotaMapper;

    public ProtectoraController(ProtectoraService service, ProtectoraMapper mapper,
            MascotaService mascotaService, MascotaMapper mascotaMapper) {
        this.service = service;
        this.mapper = mapper;
        this.mascotaService = mascotaService;
        this.mascotaMapper = mascotaMapper;
    }

    @GetMapping({"/{id}/mascotas", "/{id}/mascotas/"})
    @Operation(summary = "Consultar las mascotas de una protectora", description = "Consulta pública; [] si existe y no tiene mascotas")
    @ApiResponse(responseCode = "200", description = "Mascotas de la protectora", content = @Content(
            mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = MascotaResponse.class))))
    @ApiResponse(responseCode = "404", description = "Protectora inexistente", content = @Content(
            mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "400", description = "ID no numérico o fuera del rango Long", content = @Content(
            mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public List<MascotaResponse> findMascotas(@PathVariable("id") Long id) {
        return mascotaService.findByProtectoraId(id).stream().map(mascotaMapper::toResponse).toList();
    }

    @GetMapping({"", "/"})
    @Operation(summary = "Consultar todas las protectoras", description = "Consulta pública; devuelve [] si no hay registros")
    @ApiResponse(responseCode = "200", description = "Listado de protectoras", content = @Content(
            mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = ProtectoraResponse.class))))
    public List<ProtectoraResponse> findAll() {
        return service.findAll().stream().map(mapper::toResponse).toList();
    }

    @GetMapping({"/{id}", "/{id}/"})
    @Operation(summary = "Consultar una protectora por ID", description = "Consulta pública")
    @ApiResponse(responseCode = "200", description = "Protectora encontrada", content = @Content(
            mediaType = "application/json", schema = @Schema(implementation = ProtectoraResponse.class)))
    @ApiResponse(responseCode = "404", description = "Protectora inexistente", content = @Content(
            mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "400", description = "ID no numérico o fuera del rango Long", content = @Content(
            mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    public ProtectoraResponse findById(@PathVariable("id") Long id) {
        return mapper.toResponse(service.findById(id));
    }

    @PostMapping(consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar una protectora", description = "El ID enviado se ignora; el servidor genera uno nuevo")
    @SecurityRequirement(name = "basicAuth")
    @Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER, required = true,
            description = "Token de GET /api/csrf; conservar la misma cookie de sesión")
    @ApiResponse(responseCode = "201", description = "Protectora creada", content = @Content(
            mediaType = "application/json", schema = @Schema(implementation = ProtectoraResponse.class)))
    @ApiResponse(responseCode = "400", description = "JSON o datos de entrada no válidos", content = @Content(
            mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "401", description = "Falta autenticación válida, superado CSRF", content = @Content)
    @ApiResponse(responseCode = "403", description = "CSRF ausente o inválido", content = @Content)
    public ProtectoraResponse save(@Valid @RequestBody CrearProtectoraRequest request) {
        return mapper.toResponse(service.save(mapper.toEntity(request)));
    }
}
