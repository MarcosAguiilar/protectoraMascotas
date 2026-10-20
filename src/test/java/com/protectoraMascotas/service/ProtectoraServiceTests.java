package com.protectoraMascotas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.protectoraMascotas.data.ProtectoraRepository;
import com.protectoraMascotas.entity.Protectora;
import com.protectoraMascotas.exception.ProtectoraNoEncontradaException;

@ExtendWith(MockitoExtension.class)
class ProtectoraServiceTests {

    @Mock private ProtectoraRepository repository;
    private ProtectoraService service;

    @BeforeEach
    void setUp() { service = new ProtectoraService(repository); }

    @Test
    void registroDevuelveEntidadConIdGenerado() {
        Protectora nueva = new Protectora();
        Protectora guardada = new Protectora();
        guardada.setId(50L);
        when(repository.save(nueva)).thenReturn(guardada);
        assertThat(service.save(nueva)).isSameAs(guardada);
        verify(repository).save(nueva);
    }

    @Test
    void consultaPorIdDevuelveLaEntidad() {
        Protectora protectora = new Protectora();
        when(repository.findById(5L)).thenReturn(Optional.of(protectora));
        assertThat(service.findById(5L)).isSameAs(protectora);
    }

    @Test
    void consultaInexistenteLanzaExcepcionEspecifica() {
        when(repository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(999L))
                .isInstanceOf(ProtectoraNoEncontradaException.class)
                .hasMessage("No existe una protectora con ID 999.");
    }

    @Test
    void listadoConservaLosResultadosDelRepositorio() {
        List<Protectora> protectoras = List.of(new Protectora(), new Protectora());
        when(repository.findAll()).thenReturn(protectoras);
        assertThat(service.findAll()).containsExactlyElementsOf(protectoras);
    }

    @Test
    void listadoSinResultadosEsVacio() {
        when(repository.findAll()).thenReturn(List.of());
        assertThat(service.findAll()).isEmpty();
    }

    @Test
    void falloDePersistenciaSePropagaSinTransformarloEnError400() {
        Protectora nueva = new Protectora();
        var failure = new DataIntegrityViolationException("Fallo interno");
        when(repository.save(nueva)).thenThrow(failure);
        assertThatThrownBy(() -> service.save(nueva)).isSameAs(failure);
    }
}
