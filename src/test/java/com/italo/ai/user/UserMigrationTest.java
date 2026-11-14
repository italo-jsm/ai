package com.italo.ai.user;

import java.sql.*;
import java.util.UUID;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.assertj.core.api.Assertions.*;

class UserMigrationTest {
    @Test void preservesLegacyMemoriesAndSeparatesUnmappedIdentitiesOnH2() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:migration-" + UUID.randomUUID(), "sa", "")) {
            verifyMigration(connection, null, false);
        }
    }

    @Test void refusesAmbiguousOrBlankLegacyOwnersBeforeChangingTables() throws Exception {
        for (var owners : java.util.List.of(java.util.List.of("Italo", "italo"),
                java.util.List.of("telegram:5123456789", "telegram_5123456789"), java.util.List.of(" "))) {
            try (var connection = DriverManager.getConnection("jdbc:h2:mem:collision-" + UUID.randomUUID(), "sa", "");
                 var resources = new ClassLoaderResourceAccessor()) {
                var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
                new Liquibase("db/changelog/001-memory.sql", resources, database)
                        .update(new Contexts(), new LabelExpression());
                try (var insert = connection.prepareStatement("""
                        INSERT INTO memory (id, user_id, created_at, type, subject, predicate, memory_value)
                        VALUES (?, ?, CURRENT_TIMESTAMP, 'FACT', 'usuário', 'mora em', 'Recife')
                        """)) {
                    for (String owner : owners) {
                        insert.setObject(1, UUID.randomUUID());
                        insert.setString(2, owner);
                        insert.executeUpdate();
                    }
                }
                connection.commit();
                assertThatThrownBy(() -> new Liquibase("db/changelog/db.changelog-master.yaml", resources, database)
                        .update(new Contexts(), new LabelExpression()))
                        .isInstanceOf(liquibase.exception.LiquibaseException.class);
                try (var statement = connection.createStatement();
                     var rows = statement.executeQuery("SELECT user_id FROM memory")) {
                    var preserved = new java.util.ArrayList<String>();
                    while (rows.next()) preserved.add(rows.getString(1));
                    assertThat(preserved).containsExactlyInAnyOrderElementsOf(owners);
                }
                try (var statement = connection.createStatement();
                     var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'APP_USER'")) {
                    rows.next();
                    assertThat(rows.getInt(1)).isZero();
                }
            }
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_USER_MIGRATION_POSTGRES_TESTS", matches = "true")
    void migratesPostgresMemoriesAndVectorOwnersInIsolatedSchema() throws Exception {
        String url = "jdbc:postgresql://localhost:" + env("POSTGRES_PORT", "5433") + "/" + env("POSTGRES_DB", "ai");
        try (var connection = DriverManager.getConnection(url, env("POSTGRES_USER", "ai"), env("POSTGRES_PASSWORD", "ai_local"))) {
            String schema = "user_migration_" + UUID.randomUUID().toString().replace("-", "");
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA " + schema);
                connection.setSchema(schema);
                try {
                    verifyMigration(connection, schema, true);
                } finally {
                    connection.rollback();
                    connection.setSchema("public");
                    statement.execute("DROP SCHEMA " + schema + " CASCADE");
                    connection.commit();
                }
            }
        }
    }

    private void verifyMigration(Connection connection, String schema, boolean postgres) throws Exception {
        var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
        if (schema != null) {
            database.setDefaultSchemaName(schema);
            database.setLiquibaseSchemaName(schema);
        }
        try (var resources = new ClassLoaderResourceAccessor()) {
            new Liquibase("db/changelog/001-memory.sql", resources, database).update(new Contexts(), new LabelExpression());
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            UUID telegram = UUID.randomUUID();
            try (var insert = connection.prepareStatement("""
                    INSERT INTO memory (id, user_id, created_at, type, subject, predicate, memory_value)
                    VALUES (?, ?, CURRENT_TIMESTAMP, 'FACT', 'usuário', 'mora em', 'Recife')
                    """)) {
                Object[][] rows = {{first, " ItAlO "}, {second, " ItAlO "}, {telegram, "telegram:5123456789"}};
                for (var row : rows) {
                    insert.setObject(1, row[0]);
                    insert.setString(2, (String) row[1]);
                    insert.executeUpdate();
                }
            }
            if (postgres) {
                try (var statement = connection.createStatement()) {
                    // Same metadata type as PgVectorStore; an embedding is not needed for this migration.
                    statement.execute("CREATE TABLE vector_store (id UUID PRIMARY KEY, metadata JSON)");
                }
                try (var insert = connection.prepareStatement("INSERT INTO vector_store VALUES (?, CAST(? AS JSON))")) {
                    insert.setObject(1, first);
                    insert.setString(2, "{\"userId\":\" ItAlO \",\"kind\":\"memory\",\"type\":\"FACT\"}");
                    insert.executeUpdate();
                }
            }
            new Liquibase("db/changelog/db.changelog-master.yaml", resources, database)
                    .update(new Contexts(), new LabelExpression());
            try (var statement = connection.createStatement()) {
                try (var rows = statement.executeQuery("SELECT COUNT(*) FROM memory")) {
                    rows.next();
                    assertThat(rows.getInt(1)).isEqualTo(3);
                }
                try (var rows = statement.executeQuery("SELECT COUNT(*) FROM app_user")) {
                    rows.next();
                    assertThat(rows.getInt(1)).isEqualTo(2);
                }
                try (var rows = statement.executeQuery("""
                        SELECT u.username, u.telegram_user_id, COUNT(*) AS memories
                        FROM app_user u JOIN memory m ON m.user_id = u.id
                        GROUP BY u.id, u.username, u.telegram_user_id ORDER BY u.username
                        """)) {
                    rows.next();
                    assertThat(rows.getString(1)).isEqualTo("italo");
                    assertThat(rows.getObject(2)).isNull();
                    assertThat(rows.getInt(3)).isEqualTo(2);
                    rows.next();
                    assertThat(rows.getString(1)).isEqualTo("telegram_5123456789");
                    assertThat(rows.getLong(2)).isEqualTo(5123456789L);
                    assertThat(rows.getInt(3)).isEqualTo(1);
                }
                if (postgres) {
                    try (var rows = statement.executeQuery("""
                            SELECT v.metadata->>'userId' = m.user_id::text AS owner_matches,
                                   v.metadata->>'kind' AS kind, v.metadata->>'type' AS type
                            FROM vector_store v JOIN memory m ON v.id = m.id
                            """)) {
                        rows.next();
                        assertThat(rows.getBoolean(1)).isTrue();
                        assertThat(rows.getString(2)).isEqualTo("memory");
                        assertThat(rows.getString(3)).isEqualTo("FACT");
                    }
                }
            }
            try (var insert = connection.prepareStatement("""
                    INSERT INTO memory (id, user_id, created_at, type, subject, predicate, memory_value)
                    VALUES (?, ?, CURRENT_TIMESTAMP, 'FACT', 'usuário', 'mora em', 'Recife')
                    """)) {
                insert.setObject(1, UUID.randomUUID());
                insert.setObject(2, UUID.randomUUID());
                assertThatThrownBy(insert::executeUpdate).isInstanceOf(SQLException.class);
            }
        }
    }

    private static String env(String name, String fallback) {
        return System.getenv().getOrDefault(name, fallback);
    }
}
