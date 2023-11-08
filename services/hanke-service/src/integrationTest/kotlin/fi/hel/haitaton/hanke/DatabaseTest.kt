package fi.hel.haitaton.hanke

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlMergeMode
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.MountableFile

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
@Testcontainers
abstract class DatabaseTest {
    companion object {
        @Container
        private val postgresContainer: HaitatonPostgreSQLContainer =
            HaitatonPostgreSQLContainer()
                .withPassword("test")
                .withUsername("test")
                .withCopyToContainer(
                    MountableFile.forClasspathResource(
                        "/fi/hel/haitaton/hanke/tormaystarkastelu/HEL-GIS-data-test.sql"
                    ),
                    "/docker-entrypoint-initdb.d/HEL-GIS-data-test.sql"
                )

        @JvmStatic
        @DynamicPropertySource
        fun postgresqlProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgresContainer::getJdbcUrl)
            registry.add("spring.datasource.username", postgresContainer::getUsername)
            registry.add("spring.datasource.password", postgresContainer::getPassword)
            registry.add("spring.liquibase.url", postgresContainer::getJdbcUrl)
            registry.add("spring.liquibase.user", postgresContainer::getUsername)
            registry.add("spring.liquibase.password", postgresContainer::getPassword)
        }
    }
}
