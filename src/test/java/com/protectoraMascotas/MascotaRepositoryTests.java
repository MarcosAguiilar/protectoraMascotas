package com.protectoraMascotas;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Comparator;
import java.util.Date;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.protectoraMascotas.data.MascotaRepository;
import com.protectoraMascotas.entity.Mascota;

@Transactional
class MascotaRepositoryTests extends SecurityTestSupport {

    @Autowired private MascotaRepository repository;
    @Autowired private EntityManager entityManager;

    @Test
    void importSqlConservaLosDatosIniciales() {
        assertThat(repository.count()).isEqualTo(49);
        assertThat(repository.findByNombre("Luna")).singleElement().satisfies(mascota -> {
            assertThat(mascota.getId()).isEqualTo(1L);
            assertThat(mascota.getFechaNac()).isNotNull();
            assertThat(mascota.getRaza()).isEqualTo("Golden Retriever");
            assertThat(mascota.getPeso()).isEqualTo(30L);
            assertThat(mascota.isTiene_chip()).isTrue();
            assertThat(mascota.getUrl_foto()).isEqualTo("www.example.com/luna.jpg");
        });
    }

    @Test
    void crudConservaLosCamposTrasConsultarDeNuevoLaBaseDeDatos() {
        Mascota mascota = new Mascota();
        mascota.setNombre("Prueba persistencia");
        mascota.setFechaNac(Date.from(Instant.parse("2020-05-01T12:00:00Z")));
        mascota.setRaza("Mestiza");
        mascota.setPeso(12L);
        mascota.setTiene_chip(true);
        mascota.setUrl_foto("https://example.com/mascota.jpg");
        Long id = repository.saveAndFlush(mascota).getId();
        assertThat(id).isPositive();
        entityManager.clear();

        Mascota loaded = repository.findById(id).orElseThrow();
        assertThat(loaded.getNombre()).isEqualTo("Prueba persistencia");
        assertThat(loaded.getFechaNac().toInstant()).isEqualTo(Instant.parse("2020-05-01T12:00:00Z"));
        assertThat(loaded.getRaza()).isEqualTo("Mestiza");
        assertThat(loaded.getPeso()).isEqualTo(12L);
        assertThat(loaded.isTiene_chip()).isTrue();
        assertThat(loaded.getUrl_foto()).isEqualTo("https://example.com/mascota.jpg");

        loaded.setPeso(13L);
        repository.saveAndFlush(loaded);
        entityManager.clear();
        assertThat(repository.findById(id).orElseThrow().getPeso()).isEqualTo(13L);
        repository.deleteById(id);
        repository.flush();
        entityManager.clear();
        assertThat(repository.findById(id)).isEmpty();
        assertThat(repository.count()).isEqualTo(49);
    }

    @Test
    void busquedaPaginacionYOrdenPorNacimientoSiguenFuncionando() {
        assertThat(repository.findByNombre("luna")).isEmpty();
        assertThat(repository.findByNombre("NoExiste")).isEmpty();
        Page<Mascota> page = repository.findAll(PageRequest.of(0, 5));
        assertThat(page.getContent()).hasSize(5);
        assertThat(page.getTotalElements()).isEqualTo(49);
        assertThat(page.getTotalPages()).isEqualTo(10);
        assertThat(repository.findAllByOrderByFechaNacDesc(PageRequest.of(0, 20)).getContent())
                .hasSize(20).extracting(Mascota::getFechaNac)
                .isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    void borrarIdInexistenteUsaElComportamientoEstandarDeJpaRepository() {
        repository.deleteById(999999L);
        repository.flush();
        assertThat(repository.count()).isEqualTo(49);
    }
}
