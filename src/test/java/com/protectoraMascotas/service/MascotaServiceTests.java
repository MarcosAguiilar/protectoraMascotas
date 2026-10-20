package com.protectoraMascotas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.protectoraMascotas.data.MascotaRepository;
import com.protectoraMascotas.data.ProtectoraRepository;
import com.protectoraMascotas.entity.Protectora;
import com.protectoraMascotas.exception.ProtectoraNoEncontradaException;
import com.protectoraMascotas.entity.Mascota;
import com.protectoraMascotas.exception.MascotaNoEncontradaException;
import com.protectoraMascotas.exception.PaginaInvalidaException;

@ExtendWith(MockitoExtension.class)
class MascotaServiceTests {

    @Mock private MascotaRepository repository;
    @Mock private ProtectoraRepository protectoraRepository;
    private MascotaService service;

    @BeforeEach
    void setUp() {
        service = new MascotaService(repository, protectoraRepository);
    }

    @Test
    void consultaPorIdDevuelveLaMascotaEncontrada() {
        Mascota luna = mascota(1L, "Luna");
        when(repository.findById(1L)).thenReturn(Optional.of(luna));

        assertThat(service.findById(1L)).isSameAs(luna);
    }

    @Test
    void consultaPorIdInexistenteLanzaExcepcionDeDominio() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(999L))
                .isInstanceOf(MascotaNoEncontradaException.class)
                .hasMessage("No existe una mascota con ID 999.");
    }

    @Test
    void busquedaPorNombreConservaLaBusquedaExactaYSusResultados() {
        List<Mascota> mascotas = List.of(mascota(1L, "Luna"), mascota(2L, "Luna"));
        when(repository.findByNombre("Luna")).thenReturn(mascotas);

        assertThat(service.findByNombre("Luna")).containsExactlyElementsOf(mascotas);
        verify(repository).findByNombre("Luna");
    }

    @Test
    void busquedaSinResultadosDevuelveListaVacia() {
        when(repository.findByNombre("NoExiste")).thenReturn(List.of());

        assertThat(service.findByNombre("NoExiste")).isEmpty();
    }

    @Test
    void registroDevuelveElResultadoDelRepositorioConElIdGenerado() {
        Mascota nueva = mascota(null, "Nueva");
        Mascota guardada = mascota(50L, "Nueva");
        when(repository.save(nueva)).thenReturn(guardada);

        Mascota result = service.save(nueva, null);

        assertThat(result).isSameAs(guardada);
        assertThat(result.getId()).isEqualTo(50L);
        verify(repository).save(nueva);
        verifyNoInteractions(protectoraRepository);
    }

    @Test
    void registroPropagaLosErroresDePersistencia() {
        Mascota nueva = mascota(null, "Nueva");
        DataIntegrityViolationException failure = new DataIntegrityViolationException("Datos rechazados");
        when(repository.save(nueva)).thenThrow(failure);

        assertThatThrownBy(() -> service.save(nueva, null)).isSameAs(failure);
    }

    @Test
    void registroResuelveLaProtectoraAntesDeGuardar() {
        Protectora protectora = new Protectora();
        protectora.setId(3L);
        Mascota nueva = mascota(null, "Nueva");
        when(protectoraRepository.findById(3L)).thenReturn(Optional.of(protectora));
        when(repository.save(nueva)).thenReturn(nueva);
        assertThat(service.save(nueva, 3L).getProtectora()).isSameAs(protectora);
        verify(repository).save(nueva);
    }

    @Test
    void registroConProtectoraInexistenteNoGuarda() {
        when(protectoraRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.save(mascota(null, "Nueva"), 999L))
                .isInstanceOf(ProtectoraNoEncontradaException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void consultaDeProtectoraInexistenteNoConsultaMascotas() {
        when(protectoraRepository.existsById(999L)).thenReturn(false);
        assertThatThrownBy(() -> service.findByProtectoraId(999L))
                .isInstanceOf(ProtectoraNoEncontradaException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void consultaPorProtectoraUtilizaSuId() {
        when(protectoraRepository.existsById(3L)).thenReturn(true);
        Mascota luna = mascota(1L, "Luna");
        when(repository.findByProtectoraId(3L)).thenReturn(List.of(luna));
        assertThat(service.findByProtectoraId(3L)).containsExactly(luna);
    }

    @Test
    void protectoraSinMascotasDevuelveListaVacia() {
        when(protectoraRepository.existsById(3L)).thenReturn(true);
        when(repository.findByProtectoraId(3L)).thenReturn(List.of());
        assertThat(service.findByProtectoraId(3L)).isEmpty();
    }

    @Test
    void listadoConservaElOrdenDelRepositorio() {
        Mascota max = mascota(2L, "Max");
        Mascota luna = mascota(1L, "Luna");
        when(repository.findAll()).thenReturn(List.of(max, luna));

        assertThat(service.findAll()).containsExactly(max, luna);
    }

    @Test
    void consultaDeLasMasJovenesSolicitaLasPrimerasVeinteSinCambiarElOrden() {
        List<Mascota> mascotas = List.of(mascota(2L, "Joven"), mascota(1L, "Mayor"));
        when(repository.findAllByOrderByFechaNacDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(mascotas));

        assertThat(service.findTwentyRecent()).containsExactlyElementsOf(mascotas);
        verify(repository).findAllByOrderByFechaNacDesc(PageRequest.of(0, 20));
    }

    @Test
    void consultaDeLasMasJovenesPuedeDevolverListaVacia() {
        when(repository.findAllByOrderByFechaNacDesc(any(Pageable.class))).thenReturn(Page.empty());

        assertThat(service.findTwentyRecent()).isEmpty();
    }

    @Test
    void paginacionConservaIndiceTamanoContenidoYMetadatos() {
        List<Mascota> mascotas = List.of(mascota(16L, "Pagina tres"));
        Page<Mascota> page = new PageImpl<>(mascotas, PageRequest.of(3, 5), 49);
        when(repository.findAll(any(Pageable.class))).thenReturn(page);

        Page<Mascota> result = service.findAllByPage(3);

        assertThat(result.getContent()).containsExactlyElementsOf(mascotas);
        assertThat(result.getNumber()).isEqualTo(3);
        assertThat(result.getSize()).isEqualTo(5);
        assertThat(result.getTotalElements()).isEqualTo(49);
        assertThat(result.getTotalPages()).isEqualTo(10);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(pageable.capture());
        assertThat(pageable.getValue()).isEqualTo(PageRequest.of(3, 5));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, 429496730, Integer.MAX_VALUE})
    void paginaInvalidaSeRechazaAntesDeConsultarElRepositorio(Integer page) {
        assertThatThrownBy(() -> service.findAllByPage(page)).isInstanceOf(PaginaInvalidaException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void eliminacionUtilizaElIdSolicitado() {
        Mascota existente = mascota(7L, "Existente");
        when(repository.findById(7L)).thenReturn(Optional.of(existente));
        service.deleteById(7L);

        verify(repository).delete(existente);
    }

    @Test
    void eliminacionInexistenteLanzaExcepcionDeDominioSinBorrar() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteById(999L)).isInstanceOf(MascotaNoEncontradaException.class);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).delete(any(Mascota.class));
    }

    private static Mascota mascota(Long id, String nombre) {
        Mascota mascota = new Mascota();
        mascota.setId(id);
        mascota.setNombre(nombre);
        return mascota;
    }
}
