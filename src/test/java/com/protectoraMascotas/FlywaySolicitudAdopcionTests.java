package com.protectoraMascotas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class FlywaySolicitudAdopcionTests {

    @Test
    void v4ConservaDatosPreviosYPermitePersistirSolicitudConFechaUtc() throws Exception {
        String url = database();
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").target("3").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO protectora (id, nombre) VALUES (20, 'Protectora existente')");
            sql.executeUpdate("INSERT INTO mascota (id, nombre, tiene_chip, protectora_id) VALUES (10, 'Mascota existente', true, 20)");
        }
        Flyway flyway = flyway(url);
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        flyway.validate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
            try (var rows = sql.executeQuery("SELECT m.nombre, m.protectora_id, p.nombre FROM mascota m JOIN protectora p ON p.id = m.protectora_id WHERE m.id = 10")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("Mascota existente");
                assertThat(rows.getLong(2)).isEqualTo(20);
                assertThat(rows.getString(3)).isEqualTo("Protectora existente");
            }
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM solicitud_adopcion")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isZero();
            }
            try (var insert = connection.prepareStatement("INSERT INTO solicitud_adopcion (mascota_id, nombre_solicitante, email_solicitante, fecha_solicitud, estado) VALUES (?, ?, ?, ?, ?)")) {
                insert.setLong(1, 10);
                insert.setString(2, "Prueba");
                insert.setString(3, "prueba@example.org");
                insert.setObject(4, OffsetDateTime.parse("2026-01-01T13:00:00+01:00"));
                insert.setString(5, "PENDIENTE");
                insert.executeUpdate();
            }
            try (var rows = sql.executeQuery("SELECT id, mascota_id, nombre_solicitante, email_solicitante, fecha_solicitud, estado FROM solicitud_adopcion")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong("id")).isPositive();
                assertThat(rows.getLong("mascota_id")).isEqualTo(10);
                assertThat(rows.getString("nombre_solicitante")).isEqualTo("Prueba");
                assertThat(rows.getString("email_solicitante")).isEqualTo("prueba@example.org");
                assertThat(rows.getObject("fecha_solicitud", OffsetDateTime.class).toInstant())
                        .isEqualTo(Instant.parse("2026-01-01T12:00:00Z"));
                assertThat(rows.getString("estado")).isEqualTo("PENDIENTE");
            }
        }
        assertThat(flyway(url).migrate().migrationsExecuted).isZero();
    }

    @Test
    void restriccionesProtegenReferenciasEstadoYCamposObligatorios() throws Exception {
        String url = database();
        flyway(url).migrate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO mascota (id, nombre, tiene_chip) VALUES (10, 'Prueba', false)");
            String insert = "INSERT INTO solicitud_adopcion (mascota_id, nombre_solicitante, email_solicitante, fecha_solicitud, estado) ";
            assertThatThrownBy(() -> sql.executeUpdate(insert + "VALUES (99999, 'Prueba', 'prueba@example.org', CURRENT_TIMESTAMP, 'PENDIENTE')"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> sql.executeUpdate(insert + "VALUES (NULL, 'Prueba', 'prueba@example.org', CURRENT_TIMESTAMP, 'PENDIENTE')"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> sql.executeUpdate(insert + "VALUES (10, NULL, 'prueba@example.org', CURRENT_TIMESTAMP, 'PENDIENTE')"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> sql.executeUpdate(insert + "VALUES (10, 'Prueba', NULL, CURRENT_TIMESTAMP, 'PENDIENTE')"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> sql.executeUpdate(insert + "VALUES (10, 'Prueba', 'prueba@example.org', NULL, 'PENDIENTE')"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> sql.executeUpdate(insert + "VALUES (10, 'Prueba', 'prueba@example.org', CURRENT_TIMESTAMP, 'INVALIDO')"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> sql.executeUpdate(insert + "VALUES (10, 'Prueba', 'prueba@example.org', CURRENT_TIMESTAMP, NULL)"))
                    .isInstanceOf(SQLException.class);
            sql.executeUpdate(insert + "VALUES (10, 'Prueba', 'prueba@example.org', CURRENT_TIMESTAMP, 'PENDIENTE')");
            assertThatThrownBy(() -> sql.executeUpdate("DELETE FROM mascota WHERE id = 10"))
                    .isInstanceOf(SQLException.class);
        }
    }

    private static String database() {
        return "jdbc:h2:mem:solicitud_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    }

    private static Flyway flyway(String url) {
        return Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
                .baselineOnMigrate(false).cleanDisabled(true).load();
    }
}
