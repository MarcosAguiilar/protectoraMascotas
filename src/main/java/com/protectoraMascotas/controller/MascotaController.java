package com.protectoraMascotas.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
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

import com.protectoraMascotas.data.MascotaRepository;
import com.protectoraMascotas.entity.Mascota;

@RestController
@RequestMapping(path="/api/mascotas", produces="application/json")
public class MascotaController {

	@Autowired
	private MascotaRepository mascotaRepo;
	
	@GetMapping("/{id}")
	public ResponseEntity<Mascota> mascotaById(@PathVariable("id") Long id){
		Optional<Mascota> mascota = mascotaRepo.findById(id);
		if (mascota.isPresent()) {
		return new ResponseEntity<>(mascota.get(), HttpStatus.OK);
		}
	return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
	}
	
	
	@GetMapping("/nombre/{name}")
	public ResponseEntity<List<Mascota>> mascotaByNombre(@PathVariable("name") String name){
		List<Mascota> mascota = mascotaRepo.findByNombre(name);
		if (mascota.isEmpty()) {
			return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(mascota);
	}
	
	@PostMapping(consumes="application/json")
	@ResponseStatus(HttpStatus.CREATED)
	public Mascota saveMascota(@RequestBody Mascota mascota) {
		Mascota saved = mascotaRepo.save(mascota);
		return saved;
	}
	
	@GetMapping
	public Iterable<Mascota> findAll(){
		return mascotaRepo.findAll();
	}
	
	@GetMapping("/paged20")
    public Iterable<Mascota> findTwentyRecent() {
        PageRequest pageRequest = PageRequest.of(0, 20);
        return mascotaRepo.findAllByOrderByFechaNacDesc(pageRequest).getContent();
    }
	
	@GetMapping("/paged/{pag}")
	public Iterable<Mascota> findAllByPage(@PathVariable("pag") Integer pag){
		PageRequest pageRequest = PageRequest.of(pag, 5);
		return mascotaRepo.findAll(pageRequest);
	}
	
	@DeleteMapping("/delete/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteMascotaById(@PathVariable("id") Long id) {
		mascotaRepo.deleteById(id);
	}
}
