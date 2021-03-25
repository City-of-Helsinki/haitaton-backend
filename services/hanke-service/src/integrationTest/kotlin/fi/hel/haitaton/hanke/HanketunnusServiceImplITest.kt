package fi.hel.haitaton.hanke

import assertk.assertThat
import assertk.assertions.isLessThan
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
internal class HanketunnusServiceImplITest {

    companion object {
        @Container
        var container: HaitatonPostgreSQLContainer = HaitatonPostgreSQLContainer()
            .withExposedPorts(5433) // use non-default port
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
    lateinit var hanketunnusService: HanketunnusService

    @Test
    @Transactional
    fun newHanketunnus() {
        val hanketunnus1 = hanketunnusService.newHanketunnus()
        val hanketunnus2 = hanketunnusService.newHanketunnus()

        println("hanketunnus 1: $hanketunnus1")
        println("hanketunnus 2: $hanketunnus2")
        val serial1 = hanketunnus1.substringAfterLast('-').toInt()
        val serial2 = hanketunnus2.substringAfterLast('-').toInt()
        // hanketunnus pattern is HAIYY-N where YY is the current year (2 last digits)
        // and N is a increasing serial number
        assertThat(serial1).isLessThan(serial2)
    }
}
