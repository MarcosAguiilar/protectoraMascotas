package com.protectoraMascotas.mapper;

import org.springframework.stereotype.Component;

import com.protectoraMascotas.dto.CrearProtectoraRequest;
import com.protectoraMascotas.dto.ProtectoraResponse;
import com.protectoraMascotas.entity.Protectora;

@Component
public class ProtectoraMapper {

    public Protectora toEntity(CrearProtectoraRequest request) {
        Protectora protectora = new Protectora();
        protectora.setNombre(request.nombre());
        protectora.setEmail(request.email());
        protectora.setTelefono(request.telefono());
        protectora.setMunicipio(request.municipio());
        return protectora;
    }

    public ProtectoraResponse toResponse(Protectora protectora) {
        return new ProtectoraResponse(protectora.getId(), protectora.getNombre(), protectora.getEmail(),
                protectora.getTelefono(), protectora.getMunicipio());
    }
}
