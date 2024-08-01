package com.protectoraMascotas.entity;

import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.Data;

@Entity
@Table(name="mascota")
@Data
public class Mascota {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private String nombre;
	@Column(name = "FECHANAC")
	private Date fechaNac;
	private String raza;
	private Long peso;
	private boolean tiene_chip;
	private String url_foto;
	
	
	

	
	
	
	
}
