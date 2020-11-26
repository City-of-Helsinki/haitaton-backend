package fi.hel.haitaton.hanke.geometria

import assertk.assertAll
import assertk.assertThat
import assertk.assertions.isEqualTo
import fi.hel.haitaton.hanke.*
import mu.KotlinLogging
import org.geojson.Point
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.file.Files
import java.nio.file.Paths

private val logger = KotlinLogging.logger { }

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
internal class HankeGeometriatServiceImplITest {

    companion object {
        @Container
        var container: HaitatonPostgreSQLContainer = HaitatonPostgreSQLContainer
                .withExposedPorts(5433)
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
    private lateinit var hankeRepository: HankeRepository

    @Autowired
    private lateinit var hankeGeometriatDao: HankeGeometriatDao

    @Autowired
    private lateinit var hankeGeometriatService: HankeGeometriatService

    @Autowired
    private lateinit var jdbcOperations: JdbcOperations
/*
    @BeforeEach
    fun setUp() {
        // delete all
        jdbcOperations.execute("DELETE FROM HankeGeometriat")
    }
*/
    @Test
    fun `save and load`() {
        val hankeTunnus = "123456"
        val hankeGeometriat = OBJECT_MAPPER.readValue(Files.readString(Paths.get("src/integrationTest/resources/fi/hel/haitaton/hanke/hankeGeometriat.json")), HankeGeometriat::class.java)
        hankeGeometriat.createdByUserId = 1111
        hankeGeometriat.updatedByUserId = 2222
        val hankeId = hankeGeometriat.hankeId
        // For FK constraints we need a Hanke in database
        hankeRepository.save(HankeEntity(id = hankeGeometriat.hankeId, hankeTunnus = hankeTunnus))

        hankeGeometriatService.saveGeometriat(hankeTunnus, hankeGeometriat)
        // load
        val loadedHankeGeometriat = hankeGeometriatService.loadGeometriat(hankeTunnus)

        assertAll {
            assertThat(loadedHankeGeometriat!!.hankeId).isEqualTo(hankeGeometriat.hankeId)
            assertThat(loadedHankeGeometriat.version).isEqualTo(hankeGeometriat.version)
            assertThat(loadedHankeGeometriat.createdByUserId).isEqualTo(hankeGeometriat.createdByUserId)
            assertThat(loadedHankeGeometriat.createdAt!!.format(DATABASE_TIMESTAMP_FORMAT)).isEqualTo(hankeGeometriat.createdAt!!.format(DATABASE_TIMESTAMP_FORMAT))
            assertThat(loadedHankeGeometriat.updatedByUserId).isEqualTo(hankeGeometriat.updatedByUserId)
            assertThat(loadedHankeGeometriat.updatedAt!!.format(DATABASE_TIMESTAMP_FORMAT)).isEqualTo(hankeGeometriat.updatedAt!!.format(DATABASE_TIMESTAMP_FORMAT))
            assertThat(loadedHankeGeometriat.featureCollection!!.features.size).isEqualTo(hankeGeometriat.featureCollection!!.features.size)
            assertThat(loadedHankeGeometriat.featureCollection!!.features[0].geometry is Point)
            val loadedPoint = loadedHankeGeometriat.featureCollection!!.features[0].geometry as Point
            val point = hankeGeometriat.featureCollection!!.features[0].geometry as Point
            assertThat(loadedPoint.coordinates).isEqualTo(point.coordinates)
        }
    }
}