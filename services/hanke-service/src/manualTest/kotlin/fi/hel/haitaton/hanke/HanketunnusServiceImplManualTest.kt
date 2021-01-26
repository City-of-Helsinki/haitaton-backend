package fi.hel.haitaton.hanke

import assertk.assertThat
import assertk.assertions.isEqualTo
import mu.KotlinLogging
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.IntStream

private val logger = KotlinLogging.logger { }

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
internal class HanketunnusServiceImplManualTest {

    companion object {
        @Container
        var container: HaitatonPostgreSQLContainer = HaitatonPostgreSQLContainer
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
        val ids = ConcurrentHashMap.newKeySet<String>()
        IntStream.range(0, 100).parallel().forEach { i ->
            (0 until 100).forEach { j ->
                val hanketunnus = hanketunnusService.newHanketunnus()
                ids.add(hanketunnus)
                logger.debug {
                    "${i * 100 + j} - $hanketunnus"
                }
                if (Math.random() > 0.5) {
                    Thread.sleep((Math.random() * 5).toLong())
                } else {
                    var c = 0
                    (0..(1000 * Math.random()).toInt() + 1000).forEach { k ->
                        c += k
                    }
                }
            }
        }
        assertThat(ids.size).isEqualTo(10000)
    }
}