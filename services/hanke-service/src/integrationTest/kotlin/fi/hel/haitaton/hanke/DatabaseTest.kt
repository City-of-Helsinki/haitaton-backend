package fi.hel.haitaton.hanke

import org.springframework.jdbc.core.RowMapper
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlMergeMode
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.utility.MountableFile

val countMapper = RowMapper { rs, _ -> rs.getInt(1) }

/**
 * Start a Docker container running PostgreSQL with the PostGIS extension. Tests extending this
 * class can call the database through Autowired repositories. The database is reused for all tests
 * during one run. After the run, the container is removed.
 *
 * The database is cleaned before each test. Entity tables are emptied, but the `tormays_*` tables
 * are kept. Sequences are also not reset, including the `idcounter` table.
 *
 * The various tormays_* tables are populated when the database is first started.
 */
@Sql("/clear-db.sql")
@SqlMergeMode(SqlMergeMode.MergeMode.MERGE)
abstract class DatabaseTest {
    companion object {
        @Container
        private val container: HaitatonPostgreSQLContainer =
            HaitatonPostgreSQLContainer()
                .withExposedPorts(5433) // use non-default port
                .withPassword("test")
                .withUsername("test")
                .withCopyFileToContainer(
                    MountableFile.forClasspathResource(
                        "/fi/hel/haitaton/hanke/tormaystarkastelu/HEL-GIS-data-test.sql"
                    ),
                    "/docker-entrypoint-initdb.d/HEL-GIS-data-test.sql"
                )

        @JvmStatic
        @DynamicPropertySource
        fun postgresqlProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", container::getJdbcUrl)
            registry.add("spring.datasource.username", container::getUsername)
            registry.add("spring.datasource.password", container::getPassword)
            registry.add("spring.liquibase.url", container::getJdbcUrl)
            registry.add("spring.liquibase.user", container::getUsername)
            registry.add("spring.liquibase.password", container::getPassword)
        }
    }
}
