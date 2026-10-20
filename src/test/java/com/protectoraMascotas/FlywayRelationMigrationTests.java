package com.protectoraMascotas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class FlywayRelationMigrationTests {

    @Test
    void v3ConservaLosDatosDeV2YDejaLaAsociacionEnNull() throws Exception {
        String url = database();
        Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration").target("2").load().migrate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO protectora (id, nombre) VALUES (20, 'Existente')");
            sql.executeUpdate("""
                    INSERT INTO mascota (id, nombre, fechanac, raza, peso, tiene_chip, url_foto)
                    VALUES (10, 'Anterior', TIMESTAMP '2020-05-01 12:00:00', 'Mestiza', 12, true, 'foto.jpg')
                    """);
        }
        Flyway flyway = Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
                .target("3").load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        flyway.validate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
            try (var row = sql.executeQuery("SELECT * FROM mascota WHERE id = 10")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("nombre")).isEqualTo("Anterior");
                assertThat(row.getTimestamp("fechanac").toLocalDateTime())
                        .isEqualTo(java.time.LocalDateTime.of(2020, 5, 1, 12, 0));
                assertThat(row.getString("raza")).isEqualTo("Mestiza");
                assertThat(row.getLong("peso")).isEqualTo(12);
                assertThat(row.getBoolean("tiene_chip")).isTrue();
                assertThat(row.getString("url_foto")).isEqualTo("foto.jpg");
                assertThat(row.getObject("protectora_id")).isNull();
            }
            sql.executeUpdate("INSERT INTO mascota (nombre, tiene_chip, protectora_id) VALUES ('Sin protectora', false, NULL)");
            try (var row = sql.executeQuery("SELECT nombre FROM protectora WHERE id = 20")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString(1)).isEqualTo("Existente");
            }
        }
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    void claveForaneaRechazaInsertYUpdateInvalidosYBorradoDeProtectoraReferenciada() throws Exception {
        String url = database();
        flyway(url).migrate();
        try (var connection = DriverManager.getConnection(url, "sa", ""); var sql = connection.createStatement()) {
            sql.executeUpdate("INSERT INTO protectora (id, nombre) VALUES (20, 'Valida')");
            sql.executeUpdate("INSERT INTO mascota (id, nombre, tiene_chip, protectora_id) VALUES (10, 'Asociada', true, 20)");
            assertThatThrownBy(() -> sql.executeUpdate(
                    "INSERT INTO mascota (nombre, tiene_chip, protectora_id) VALUES ('Invalida', false, 999999)"))
                    .isInstanceOf(SQLException.class).satisfies(error ->
                            assertThat(((SQLException) error).getSQLState()).startsWith("23"));
            assertThatThrownBy(() -> sql.executeUpdate("UPDATE mascota SET protectora_id = 999999 WHERE id = 10"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> sql.executeUpdate("DELETE FROM protectora WHERE id = 20"))
                    .isInstanceOf(SQLException.class);
            try (var row = sql.executeQuery("SELECT protectora_id FROM mascota WHERE id = 10")) {
                assertThat(row.next()).isTrue();
                assertThat(row.getLong(1)).isEqualTo(20);
            }
        }
    }

    private static Flyway flyway(String url) {
        return Flyway.configure().dataSource(url, "sa", "").locations("classpath:db/migration")
                .cleanDisabled(true).baselineOnMigrate(false).load();
    }

    private static String database() {
        return "jdbc:h2:mem:relation_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    }
}
