package com.protectoraMascotas.dto;

import java.util.List;

public record PaginaSolicitudesAdopcionResponse(List<SolicitudAdopcionResponse> content,
        int number, int size, long totalElements, int totalPages, boolean first, boolean last) {
}
