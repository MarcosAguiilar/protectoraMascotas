package com.protectoraMascotas.controller;

import java.util.List;
import java.util.Optional;

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
		Optional<MascotaResponse> mascota = mascotaService.findById(id).map(mascotaMapper::toResponse);
		if (mascota.isPresent()) {
		return new ResponseEntity<>(mascota.get(), HttpStatus.OK);
		}
	return ResponseEntity.notFound().build();
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
	@SecurityRequirement(name = "basicAuth")
	@Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER, required = true,
			description = "Token obtenido de GET /api/csrf; conservar la misma cookie de sesión")
	@ResponseStatus(HttpStatus.CREATED)
	public MascotaResponse saveMascota(@Valid @RequestBody CrearMascotaRequest mascota) {
		return mascotaMapper.toResponse(mascotaService.save(mascotaMapper.toEntity(mascota)));
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
	@SecurityRequirement(name = "basicAuth")
	@Parameter(name = "X-CSRF-TOKEN", in = ParameterIn.HEADER, required = true,
			description = "Token obtenido de GET /api/csrf; conservar la misma cookie de sesión")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteMascotaById(@PathVariable("id") Long id) {
		mascotaService.deleteById(id);
	}
}
