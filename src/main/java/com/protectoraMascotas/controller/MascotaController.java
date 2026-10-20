package com.protectoraMascotas.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.protectoraMascotas.dto.CrearMascotaRequest;
import com.protectoraMascotas.dto.MascotaResponse;
import com.protectoraMascotas.mapper.MascotaMapper;
import com.protectoraMascotas.service.MascotaService;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.ProblemDetail;

@RestController
@RequestMapping(path="/api/mascotas", produces="application/json")
public class MascotaController {

	private final MascotaService mascotaService;
	private final MascotaMapper mascotaMapper;

	public MascotaController(MascotaService mascotaService, MascotaMapper mascotaMapper) {
		this.mascotaService = mascotaService;
		this.mascotaMapper = mascotaMapper;
	}
	
	@GetMapping({"/{id}", "/{id}/"})
	public ResponseEntity<MascotaResponse> mascotaById(@PathVariable("id") Long id){
		return ResponseEntity.ok(mascotaMapper.toResponse(mascotaService.findById(id)));
	}
	
	
	@GetMapping({"/nombre/{name}", "/nombre/{name}/"})
	public ResponseEntity<List<MascotaResponse>> mascotaByNombre(@PathVariable("name") String name){
		List<MascotaResponse> mascota = mascotaService.findByNombre(name).stream()
				.map(mascotaMapper::toResponse).toList();
		if (mascota.isEmpty()) {
			return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(mascota);
	}
	
	@PostMapping(consumes="application/json")
	@Operation(description = "Registra una mascota; protectoraId es opcional y debe corresponder a una protectora existente")
	@ApiResponse(responseCode = "201", description = "Mascota creada", content = @Content(
			mediaType = "application/json", schema = @Schema(implementation = MascotaResponse.class)))
	@ApiResponse(responseCode = "400", description = "JSON o datos inválidos, incluido protectoraId no positivo", content = @Content(
			mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "404", description = "La protectora indicada no existe", content = @Content(
			mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@SecurityRequirement(name = "basicAuth")
	@Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER, required = true,
			description = "Token obtenido de GET /api/csrf; conservar la misma cookie de sesión")
	@ResponseStatus(HttpStatus.CREATED)
	public MascotaResponse saveMascota(@Valid @RequestBody CrearMascotaRequest mascota) {
		return mascotaMapper.toResponse(mascotaService.save(mascotaMapper.toEntity(mascota), mascota.protectoraId()));
	}
	
	@GetMapping({"", "/"})
	public Iterable<MascotaResponse> findAll(){
		return mascotaService.findAll().stream().map(mascotaMapper::toResponse).toList();
	}
	
	@GetMapping({"/paged20", "/paged20/"})
    public Iterable<MascotaResponse> findTwentyRecent() {
        return mascotaService.findTwentyRecent().stream().map(mascotaMapper::toResponse).toList();
    }
	
	@GetMapping({"/paged/{pag}", "/paged/{pag}/"})
	public Iterable<MascotaResponse> findAllByPage(@PathVariable("pag") Integer pag){
		return mascotaService.findAllByPage(pag).map(mascotaMapper::toResponse);
	}
	
	@DeleteMapping("/delete/{id}")
	@ApiResponse(responseCode = "204", description = "Mascota eliminada", content = @Content)
	@ApiResponse(responseCode = "404", description = "Mascota inexistente", content = @Content(
			mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "409", description = "La mascota tiene solicitudes de adopción; no se elimina ningún dato", content = @Content(
			mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	@SecurityRequirement(name = "basicAuth")
	@Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER, required = true,
			description = "Token obtenido de GET /api/csrf; conservar la misma cookie de sesión")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteMascotaById(@PathVariable("id") Long id) {
		mascotaService.deleteById(id);
	}
}
