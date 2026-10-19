package com.protectoraMascotas.mapper;

import java.util.Date;

import org.springframework.stereotype.Component;

import com.protectoraMascotas.dto.CrearMascotaRequest;
import com.protectoraMascotas.dto.MascotaResponse;
import com.protectoraMascotas.entity.Mascota;

@Component
public class MascotaMapper {

    public Mascota toEntity(CrearMascotaRequest request) {
        Mascota mascota = new Mascota();
        mascota.setNombre(request.nombre());
        mascota.setFechaNac(copyDate(request.fechaNac()));
        mascota.setRaza(request.raza());
        mascota.setPeso(request.peso());
        mascota.setTiene_chip(request.tieneChip());
        mascota.setUrl_foto(request.urlFoto());
        return mascota;
    }

    public MascotaResponse toResponse(Mascota mascota) {
        return new MascotaResponse(mascota.getId(), mascota.getNombre(), copyDate(mascota.getFechaNac()),
                mascota.getRaza(), mascota.getPeso(), mascota.isTiene_chip(), mascota.getUrl_foto());
    }

    private Date copyDate(Date date) {
        return date == null ? null : new Date(date.getTime());
    }
}
