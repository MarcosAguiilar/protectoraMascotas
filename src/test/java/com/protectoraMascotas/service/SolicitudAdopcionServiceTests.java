package com.protectoraMascotas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import com.protectoraMascotas.data.MascotaRepository;
import com.protectoraMascotas.data.SolicitudAdopcionRepository;
import com.protectoraMascotas.entity.EstadoSolicitud;
import com.protectoraMascotas.entity.Mascota;
import com.protectoraMascotas.entity.Protectora;
import com.protectoraMascotas.entity.SolicitudAdopcion;
import com.protectoraMascotas.exception.MascotaNoEncontradaException;
import com.protectoraMascotas.exception.MascotaSinProtectoraException;
import com.protectoraMascotas.exception.PaginacionSolicitudInvalidaException;
import com.protectoraMascotas.exception.SolicitudAdopcionNoEncontradaException;
import com.protectoraMascotas.exception.ConflictoEstadoSolicitudException;
import com.protectoraMascotas.exception.EstadoSolicitudInvalidoException;

@ExtendWith(MockitoExtension.class)
class SolicitudAdopcionServiceTests {

    @Mock private SolicitudAdopcionRepository repository;
    @Mock private MascotaRepository mascotas;
    private SolicitudAdopcionService service;

    @BeforeEach
    void setUp() { service = new SolicitudAdopcionService(repository, mascotas); }

    @Test
    void registroFijaMascotaFechaEstadoEIdDeServidor() {
        Mascota mascota = new Mascota();
        mascota.setProtectora(new Protectora());
        when(mascotas.findByIdForUpdate(7L)).thenReturn(Optional.of(mascota));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SolicitudAdopcion solicitud = new SolicitudAdopcion();
        solicitud.setId(999L);
        solicitud.setEstado(EstadoSolicitud.APROBADA);
        solicitud.setFechaSolicitud(Instant.EPOCH);
        Instant before = Instant.now().minusMillis(1);
        assertThat(service.save(solicitud, 7L)).isSameAs(solicitud);
        assertThat(solicitud.getId()).isNull();
        assertThat(solicitud.getMascota()).isSameAs(mascota);
        assertThat(solicitud.getEstado()).isEqualTo(EstadoSolicitud.PENDIENTE);
        assertThat(solicitud.getFechaSolicitud()).isBetween(before, Instant.now());
    }

    @Test
    void mascotaInexistenteNoInserta() {
        when(mascotas.findByIdForUpdate(7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.save(new SolicitudAdopcion(), 7L)).isInstanceOf(MascotaNoEncontradaException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void mascotaSinProtectoraNoInserta() {
        when(mascotas.findByIdForUpdate(7L)).thenReturn(Optional.of(new Mascota()));
        assertThatThrownBy(() -> service.save(new SolicitudAdopcion(), 7L)).isInstanceOf(MascotaSinProtectoraException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void consultaPorId() {
        SolicitudAdopcion solicitud = new SolicitudAdopcion();
        when(repository.findById(9L)).thenReturn(Optional.of(solicitud));
        assertThat(service.findById(9L)).isSameAs(solicitud);
    }

    @Test
    void solicitudInexistente() {
        when(repository.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(9L)).isInstanceOf(SolicitudAdopcionNoEncontradaException.class);
    }

    @Test
    void paginacionConservaPaginaTamanoYOrdenEstable() {
        PageRequest expected = PageRequest.of(2, 20, Sort.by("id").ascending());
        Page<SolicitudAdopcion> page = Page.empty(expected);
        when(repository.findAll(expected)).thenReturn(page);
        assertThat(service.findAll(2, 20)).isSameAs(page);
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,-1", "0,101", "2147483647,100"})
    void paginacionInvalidaNoConsulta(int page, int size) {
        assertThatThrownBy(() -> service.findAll(page, size)).isInstanceOf(PaginacionSolicitudInvalidaException.class);
        verifyNoInteractions(repository, mascotas);
    }

    @ParameterizedTest
    @CsvSource({"0,1", "0,100", "2147483647,1"})
    void limitesDePaginacionValidos(int page, int size) {
        when(repository.findAll(any(Pageable.class))).thenReturn(Page.empty());
        assertThat(service.findAll(page, size)).isEmpty();
    }

    @Test
    void erroresInternosSePropagan() {
        Mascota mascota = new Mascota();
        mascota.setProtectora(new Protectora());
        when(mascotas.findByIdForUpdate(7L)).thenReturn(Optional.of(mascota));
        var failure = new DataIntegrityViolationException("Error interno");
        when(repository.save(any())).thenThrow(failure);
        assertThatThrownBy(() -> service.save(new SolicitudAdopcion(), 7L)).isSameAs(failure);
    }

    @ParameterizedTest
    @EnumSource(value = EstadoSolicitud.class, names = {"APROBADA", "RECHAZADA"})
    void cambiaSoloEstadoYBloqueaAntesDeLeer(EstadoSolicitud estado) {
        SolicitudAdopcion solicitud = pendiente();
        Instant fecha = solicitud.getFechaSolicitud();
        assertThat(service.cambiarEstado(9L, estado)).isSameAs(solicitud);
        assertThat(solicitud.getEstado()).isEqualTo(estado);
        assertThat(solicitud.getFechaSolicitud()).isEqualTo(fecha);
        var orden = inOrder(repository, mascotas);
        orden.verify(repository).findMascotaIdById(9L);
        orden.verify(mascotas).findByIdForUpdate(7L);
        orden.verify(repository).findById(9L);
        if (estado == EstadoSolicitud.APROBADA) {
            orden.verify(repository).existsByMascotaIdAndEstado(7L, EstadoSolicitud.APROBADA);
        }
    }

    @ParameterizedTest
    @CsvSource({"APROBADA,APROBADA", "APROBADA,RECHAZADA", "RECHAZADA,APROBADA", "RECHAZADA,RECHAZADA"})
    void finalesNoCambian(EstadoSolicitud anterior, EstadoSolicitud nuevo) {
        SolicitudAdopcion solicitud = pendiente();
        solicitud.setEstado(anterior);
        assertThatThrownBy(() -> service.cambiarEstado(9L, nuevo))
                .isInstanceOf(ConflictoEstadoSolicitudException.class);
        assertThat(solicitud.getEstado()).isEqualTo(anterior);
        verify(repository, never()).existsByMascotaIdAndEstado(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoSolicitud.class, names = "PENDIENTE")
    @NullSource
    void pendienteONullNoSonEstadosDestino(EstadoSolicitud estado) {
        assertThatThrownBy(() -> service.cambiarEstado(9L, estado)).isInstanceOf(EstadoSolicitudInvalidoException.class);
        verifyNoInteractions(repository, mascotas);
    }

    @Test
    void cambioDeSolicitudInexistente() {
        when(repository.findMascotaIdById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.cambiarEstado(9L, EstadoSolicitud.APROBADA))
                .isInstanceOf(SolicitudAdopcionNoEncontradaException.class);
        verifyNoInteractions(mascotas);
    }

    @Test
    void noApruebaOtraSiYaHayAprobada() {
        SolicitudAdopcion solicitud = pendiente();
        when(repository.existsByMascotaIdAndEstado(7L, EstadoSolicitud.APROBADA)).thenReturn(true);
        assertThatThrownBy(() -> service.cambiarEstado(9L, EstadoSolicitud.APROBADA))
                .isInstanceOf(ConflictoEstadoSolicitudException.class);
        assertThat(solicitud.getEstado()).isEqualTo(EstadoSolicitud.PENDIENTE);
    }

    @Test
    void noRegistraSiYaHayAprobada() {
        Mascota mascota = new Mascota();
        mascota.setProtectora(new Protectora());
        when(mascotas.findByIdForUpdate(7L)).thenReturn(Optional.of(mascota));
        when(repository.existsByMascotaIdAndEstado(7L, EstadoSolicitud.APROBADA)).thenReturn(true);
        assertThatThrownBy(() -> service.save(new SolicitudAdopcion(), 7L))
                .isInstanceOf(ConflictoEstadoSolicitudException.class);
        verify(repository, never()).save(any());
    }

    private SolicitudAdopcion pendiente() {
        SolicitudAdopcion solicitud = new SolicitudAdopcion();
        solicitud.setId(9L);
        solicitud.setEstado(EstadoSolicitud.PENDIENTE);
        solicitud.setFechaSolicitud(Instant.EPOCH);
        when(repository.findMascotaIdById(9L)).thenReturn(Optional.of(7L));
        when(mascotas.findByIdForUpdate(7L)).thenReturn(Optional.of(new Mascota()));
        when(repository.findById(9L)).thenReturn(Optional.of(solicitud));
        return solicitud;
    }
}
