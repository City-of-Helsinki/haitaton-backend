package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.attachment.common.MockFileClientExtension
import fi.hel.haitaton.hanke.test.USERNAME
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlMergeMode
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
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
@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(USERNAME)
@ExtendWith(MockFileClientExtension::class)
abstract class IntegrationTest {
    companion object {
        @ServiceConnection
        private val postgresContainer: PostgreSQLContainer<*> =
            PostgreSQLContainer(
                    DockerImageName.parse("postgis/postgis:13-master")
                        .asCompatibleSubstituteFor("postgres")
                )
                .withPassword("test")
                .withUsername("test")
                .withCopyToContainer(
                    MountableFile.forClasspathResource(
                        "/fi/hel/haitaton/hanke/tormaystarkastelu/HEL-GIS-data-test.sql"
                    ),
                    "/docker-entrypoint-initdb.d/HEL-GIS-data-test.sql"
                )

        init {
            postgresContainer.start()
        }
    }
}
