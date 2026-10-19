package com.protectoraMascotas.controller;

import java.util.List;
import java.util.Optional;

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

import com.protectoraMascotas.entity.Mascota;
import com.protectoraMascotas.service.MascotaService;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping(path="/api/mascotas", produces="application/json")
public class MascotaController {

	private final MascotaService mascotaService;

	public MascotaController(MascotaService mascotaService) {
		this.mascotaService = mascotaService;
	}
	
	@GetMapping({"/{id}", "/{id}/"})
	public ResponseEntity<Mascota> mascotaById(@PathVariable("id") Long id){
		Optional<Mascota> mascota = mascotaService.findById(id);
		if (mascota.isPresent()) {
		return new ResponseEntity<>(mascota.get(), HttpStatus.OK);
		}
	return ResponseEntity.notFound().build();
	}
	
	
	@GetMapping({"/nombre/{name}", "/nombre/{name}/"})
	public ResponseEntity<List<Mascota>> mascotaByNombre(@PathVariable("name") String name){
		List<Mascota> mascota = mascotaService.findByNombre(name);
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
	public Mascota saveMascota(@RequestBody Mascota mascota) {
		return mascotaService.save(mascota);
	}
	
	@GetMapping({"", "/"})
	public Iterable<Mascota> findAll(){
		return mascotaService.findAll();
	}
	
	@GetMapping({"/paged20", "/paged20/"})
    public Iterable<Mascota> findTwentyRecent() {
        return mascotaService.findTwentyRecent();
    }
	
	@GetMapping({"/paged/{pag}", "/paged/{pag}/"})
	public Iterable<Mascota> findAllByPage(@PathVariable("pag") Integer pag){
		return mascotaService.findAllByPage(pag);
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
