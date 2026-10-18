package com.italo.ai.memory;

import org.springframework.test.context.TestPropertySource;

/** Runs the same repository contract against the local PostgreSQL schema, with rollback after each test. */
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:${POSTGRES_PORT:5433}/${POSTGRES_DB:ai}",
    "spring.datasource.driver-class-name=org.postgresql.Driver",
    "spring.liquibase.enabled=false"
})
class PostgreSqlMemoryPersistenceTest extends MemoryPersistenceTest {
}
