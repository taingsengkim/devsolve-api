package kh.edu.istad.ite.devsoleapi.feature.problem;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class ProblemMigrationPostgreSqlTest {

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:17-alpine");

    @BeforeAll
    static void migrate() throws SQLException {
        try (
                Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()
        ) {
            statement.execute("""
                    CREATE EXTENSION IF NOT EXISTS pgcrypto;
                    CREATE TYPE problem_status_enum AS ENUM (
                        'pending_approval',
                        'published',
                        'closed'
                    );
                    CREATE TABLE user_profiles (
                        id UUID PRIMARY KEY
                    );
                    CREATE TABLE categories (
                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        name VARCHAR(50) NOT NULL,
                        slug VARCHAR(50) UNIQUE NOT NULL,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    );
                    CREATE TABLE problems (
                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        author_id UUID NOT NULL,
                        category_id UUID,
                        title VARCHAR(255) NOT NULL,
                        description TEXT NOT NULL,
                        status problem_status_enum NOT NULL
                            DEFAULT 'pending_approval',
                        view_count INTEGER NOT NULL DEFAULT 0,
                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        deleted_at TIMESTAMP
                    );
                    """);
        }

        Flyway.configure()
                .dataSource(
                        POSTGRES.getJdbcUrl(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword()
                )
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("20260727"))
                .load()
                .migrate();
    }

    @Test
    void migratesProblemEnumsToCheckedVarchar() throws SQLException {
        try (
                Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("""
                        SELECT data_type
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = 'problems'
                          AND column_name = 'status'
                        """)
        ) {
            assertTrue(result.next());
            assertEquals("character varying", result.getString(1));
        }

        try (
                Connection connection = POSTGRES.createConnection("");
                Statement statement = connection.createStatement()
        ) {
            try (ResultSet tables = statement.executeQuery("""
                    SELECT
                        to_regclass('public.tags') IS NOT NULL,
                        to_regclass('public.problem_tags') IS NOT NULL
                    """)) {
                assertTrue(tables.next());
                assertTrue(tables.getBoolean(1));
                assertTrue(tables.getBoolean(2));
            }

            statement.execute("""
                    INSERT INTO problems (
                        author_id,
                        title,
                        description,
                        status
                    ) VALUES (
                        gen_random_uuid(),
                        'A migrated problem title',
                        'A migrated problem description',
                        'DRAFT'
                    )
                    """);
            assertThrows(
                    SQLException.class,
                    () -> statement.execute("""
                            INSERT INTO problems (
                                author_id,
                                title,
                                description,
                                status
                            ) VALUES (
                                gen_random_uuid(),
                                'Another migrated title',
                                'Another migrated description',
                                'draft'
                            )
                            """)
            );
        }
    }
}
