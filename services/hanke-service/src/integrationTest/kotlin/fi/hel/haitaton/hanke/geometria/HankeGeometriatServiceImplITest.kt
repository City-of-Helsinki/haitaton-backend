package fi.hel.haitaton.hanke.geometria

import fi.hel.haitaton.hanke.HaitatonPostgreSQLContainer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers


@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
internal class HankeGeometriatServiceImplITest {

    companion object {
        @Container
        var container: HaitatonPostgreSQLContainer = HaitatonPostgreSQLContainer
                .withPassword("test")
                .withUsername("test")

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

    @Autowired
    private lateinit var hankeGeometriatService: HankeGeometriatService

    @Test
    fun saveGeometriat() {
    }

    @Test
    fun loadGeometriat() {
    }
}