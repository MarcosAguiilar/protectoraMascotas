package com.protectoraMascotas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;

import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.protectoraMascotas.data.MascotaRepository;
import com.protectoraMascotas.data.ProtectoraRepository;
import com.protectoraMascotas.entity.Mascota;
import com.protectoraMascotas.entity.Protectora;
import com.protectoraMascotas.mapper.MascotaMapper;
import com.protectoraMascotas.service.MascotaService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// Sin @Transactional: las respuestas deben funcionar una vez cerrada la transacción del servicio.
class MascotaProtectoraIntegrationTests extends SecurityTestSupport {

    @Autowired private MockMvc mvc;
    @Autowired private MascotaRepository mascotas;
    @Autowired private ProtectoraRepository protectoras;
    @Autowired private MascotaService service;
    @Autowired private MascotaMapper mapper;
    @Autowired private JsonMapper jsonMapper;
    @Autowired private Environment environment;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private String name;
    private Long firstId;
    private Long secondId;
    private Long mascotaId;
    private Statistics statistics;

    @BeforeEach
    void createOwnFixtures() {
        name = "relation-" + UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            Protectora first = new Protectora();
            first.setNombre(name);
            firstId = protectoras.save(first).getId();
            Protectora second = new Protectora();
            second.setNombre(name);
            secondId = protectoras.save(second).getId();
            mascotaId = savePet(first).getId();
            savePet(first);
            savePet(second);
            savePet(null);
        });
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
    }

    @AfterEach
    void removeOnlyOwnFixtures() {
        if (statistics != null) { statistics.setStatisticsEnabled(false); }
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            mascotas.deleteAllByIdInBatch(mascotas.findByNombre(name).stream().map(Mascota::getId).toList());
            if (firstId != null && secondId != null) {
                protectoras.deleteAllByIdInBatch(List.of(firstId, secondId));
            }
        });
    }

    private Mascota savePet(Protectora protectora) {
        Mascota pet = new Mascota();
        pet.setNombre(name);
        pet.setFechaNac(Date.from(Instant.parse("2100-01-01T00:00:00Z")));
        pet.setProtectora(protectora);
        return mascotas.save(pet);
    }

    @Test
    void registroConProtectoraValidaSeConservaEnLecturas() throws Exception {
        var created = create("{\"nombre\":\"" + name + "\",\"protectoraId\":" + firstId + "}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.protectoraId").value(firstId))
                .andExpect(jsonPath("$.length()").value(8)).andReturn();
        JsonNode body = jsonMapper.readTree(created.getResponse().getContentAsString());
        assertThat(body.get("id").asLong()).isPositive();
        mvc.perform(get("/api/mascotas/" + body.get("id").asLong()))
                .andExpect(status().isOk()).andExpect(content().json(body.toString()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", ",\"protectoraId\":null"})
    void registroSinProtectoraConservaExactamenteLosSieteCampos(String extra) throws Exception {
        var result = create("{\"nombre\":\"" + name + "\"" + extra + "}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.protectoraId").doesNotExist())
                .andExpect(jsonPath("$.length()").value(7)).andReturn();
        JsonNode body = jsonMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.get("peso").isNull()).isTrue();
        assertThat(body.get("fechaNac").isNull()).isTrue();
        assertThat(body.get("raza").isNull()).isTrue();
        assertThat(body.get("url_foto").isNull()).isTrue();
        mvc.perform(get("/api/mascotas/" + body.get("id").asLong()))
                .andExpect(status().isOk()).andExpect(content().json(body.toString()));
    }

    @Test
    void protectoraInexistenteProduce404SinInsertar() throws Exception {
        long before = mascotas.count();
        create("{\"nombre\":\"" + name + "\",\"protectoraId\":9223372036854775807}")
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Protectora no encontrada"));
        assertThat(mascotas.count()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-9223372036854775808", "\"texto\"", "9223372036854775808", "{}"})
    void protectoraIdInvalidoProduce400SinInsertar(String id) throws Exception {
        long before = mascotas.count();
        create("{\"nombre\":\"" + name + "\",\"protectoraId\":" + id + "}")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
        assertThat(mascotas.count()).isEqualTo(before);
    }

    @Test
    void listaPublicaFiltraPorProtectoraSinExponerEntidades() throws Exception {
        for (String suffix : new String[]{"", "/"}) {
            mvc.perform(get("/api/protectoras/" + firstId + "/mascotas" + suffix))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[*].protectoraId", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(firstId.intValue()))))
                    .andExpect(jsonPath("$[0].protectora").doesNotExist())
                    .andExpect(jsonPath("$[0].email").doesNotExist());
        }
    }

    @Test
    void protectoraExistenteSinMascotasDevuelve200Vacio() throws Exception {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            mascotas.deleteAllByIdInBatch(mascotas.findByProtectoraId(secondId).stream().map(Mascota::getId).toList());
        });
        mvc.perform(get("/api/protectoras/" + secondId + "/mascotas"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void consultaDeProtectoraInexistenteDevuelve404() throws Exception {
        mvc.perform(get("/api/protectoras/9223372036854775807/mascotas"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @ParameterizedTest
    @ValueSource(strings = {"texto", "1.5", "9223372036854775808"})
    void parametroDeRutaInvalidoDevuelve400(String id) throws Exception {
        mvc.perform(get("/api/protectoras/" + id + "/mascotas"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
    }

    @ParameterizedTest
    @ValueSource(strings = {"all", "name", "recent", "page", "id", "protectora"})
    void lecturasFuncionanSinOsivYNoGeneranNMasUno(String query) throws Exception {
        assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        String path = switch (query) {
            case "all" -> "/api/mascotas";
            case "name" -> "/api/mascotas/nombre/" + name;
            case "recent" -> "/api/mascotas/paged20";
            case "page" -> "/api/mascotas/paged/0";
            case "id" -> "/api/mascotas/" + mascotaId;
            case "protectora" -> "/api/protectoras/" + firstId + "/mascotas";
            default -> throw new AssertionError(query);
        };
        statistics.clear();
        mvc.perform(get(path)).andExpect(status().isOk());
        long bound = List.of("recent", "page", "protectora").contains(query) ? 2 : 1;
        assertThat(statistics.getPrepareStatementCount()).isBetween(1L, bound);
    }

    @Test
    void mapeoDeEntidadesDetachedNoNecesitaConsultas() {
        List<Mascota> result = service.findByNombre(name);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(result).filteredOn(pet -> pet.getProtectora() != null)
                .allSatisfy(pet -> assertThat(Hibernate.isInitialized(pet.getProtectora())).isTrue());
        statistics.clear();
        assertThat(result.stream().map(mapper::toResponse).filter(dto -> dto.protectoraId() != null).toList()).hasSize(3);
        assertThat(statistics.getPrepareStatementCount()).isZero();
    }

    @Test
    void escriturasConservanBasicYCsrfAntesDeValidarLaRelacion() throws Exception {
        String json = "{\"nombre\":\"" + name + "\",\"protectoraId\":9223372036854775807}";
        mvc.perform(post("/api/mascotas").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnauthorized()).andExpect(header().exists("WWW-Authenticate"));
        mvc.perform(post("/api/mascotas").with(httpBasic(USERNAME, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isForbidden());
        mvc.perform(post("/api/mascotas").with(httpBasic(USERNAME, PASSWORD)).with(csrf().useInvalidToken())
                .contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isForbidden());
    }

    @Test
    void openApiDescribeElCampoOpcionalYLaNuevaConsultaPublica() throws Exception {
        mvc.perform(get("/v3/api-docs").with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.CrearMascotaRequest.properties.protectoraId.minimum").value(0))
                .andExpect(jsonPath("$.components.schemas.CrearMascotaRequest.properties.protectoraId.exclusiveMinimum").value(true))
                .andExpect(jsonPath("$.components.schemas.CrearMascotaRequest.required",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("protectoraId"))))
                .andExpect(jsonPath("$.components.schemas.MascotaResponse.properties.protectoraId").exists())
                .andExpect(jsonPath("$.paths['/api/mascotas'].post.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/protectoras/{id}/mascotas'].get.security").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/protectoras/{id}/mascotas'].get.responses['200'].content['application/json'].schema.items['$ref']")
                        .value("#/components/schemas/MascotaResponse"))
                .andExpect(jsonPath("$.paths['/api/protectoras/{id}/mascotas'].get.responses['404']").exists());
    }

    private ResultActions create(String json) throws Exception {
        return mvc.perform(post("/api/mascotas").with(httpBasic(USERNAME, PASSWORD)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
