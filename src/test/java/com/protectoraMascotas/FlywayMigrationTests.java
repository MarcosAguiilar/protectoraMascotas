package com.protectoraMascotas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;

class FlywayMigrationTests {

    @Test
    void migracionEstructuralCreaTablaVaciaEIdentificadorGenerado() throws Exception {
        String url = databaseUrl();
        Flyway flyway = flyway(url);
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        flyway.validate();

        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM mascota")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isZero();
            }
            statement.executeUpdate("INSERT INTO mascota (nombre, tiene_chip) VALUES ('Persistencia', false)");
            try (var rows = statement.executeQuery("SELECT id FROM mascota")) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isPositive();
            }
        }
    }

    @Test
    void segundaMigracionConservaLosDatosYNoRepiteLaVersion() throws Exception {
        String url = databaseUrl();
        Flyway flyway = flyway(url);
        flyway.migrate();
        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO mascota (nombre, tiene_chip) VALUES ('Conservar', true)");
        }
        // Una nueva instancia representa la ejecución de Flyway en el siguiente arranque.
        assertThat(flyway(url).migrate().migrationsExecuted).isZero();
        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT nombre FROM mascota")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo("Conservar");
            assertThat(rows.next()).isFalse();
        }
    }

    @Test
    void esquemaExistenteSinHistorialNoSeBasaNiSobrescribeAutomaticamente() throws Exception {
        String url = databaseUrl();
        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE datos_existentes (id BIGINT)");
            statement.execute("INSERT INTO datos_existentes VALUES (42)");
        }
        assertThatThrownBy(() -> flyway(url).migrate()).isInstanceOf(FlywayException.class);
        try (var connection = DriverManager.getConnection(url, "sa", "");
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT id FROM datos_existentes")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getLong(1)).isEqualTo(42);
        }
    }

    @Test
    void cleanEstaDeshabilitado() {
        Flyway flyway = flyway(databaseUrl());
        flyway.migrate();
        assertThatThrownBy(flyway::clean).isInstanceOf(FlywayException.class);
    }

    private static Flyway flyway(String url) {
        return Flyway.configure().dataSource(url, "sa", "")
                .locations("classpath:db/migration").cleanDisabled(true)
                .baselineOnMigrate(false).load();
    }

    private static String databaseUrl() {
        return "jdbc:h2:mem:migration_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
    }
}
