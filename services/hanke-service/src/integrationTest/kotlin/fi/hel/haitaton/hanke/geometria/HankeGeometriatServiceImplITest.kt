package fi.hel.haitaton.hanke.geometria

import assertk.assertAll
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import fi.hel.haitaton.hanke.*
import org.geojson.Point
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

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
internal class HankeGeometriatServiceImplITest {

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
    private lateinit var hankeRepository: HankeRepository

    @Autowired
    private lateinit var hankeGeometriatService: HankeGeometriatService

    @Autowired
    private lateinit var jdbcOperations: JdbcOperations

    @Test
    fun `save and load and update`() {
        val hankeTunnus = "123456"
        val hankeGeometriat = OBJECT_MAPPER.readValue(Files.readString(Paths.get("src/integrationTest/resources/fi/hel/haitaton/hanke/hankeGeometriat.json")), HankeGeometriat::class.java)
        hankeGeometriat.createdByUserId = 1111
        hankeGeometriat.updatedByUserId = 2222
        // For FK constraints we need a Hanke in database
        hankeRepository.save(HankeEntity(id = hankeGeometriat.hankeId, hankeTunnus = hankeTunnus))

        // save
        hankeGeometriatService.saveGeometriat(hankeTunnus, hankeGeometriat)

        // load
        var loadedHankeGeometriat = hankeGeometriatService.loadGeometriat(hankeTunnus)
        val createdAt = loadedHankeGeometriat!!.createdAt!!
        assertAll {
            assertThat(loadedHankeGeometriat!!.hankeId).isEqualTo(hankeGeometriat.hankeId)
            assertThat(loadedHankeGeometriat!!.version).isEqualTo(1)
            assertThat(loadedHankeGeometriat!!.createdByUserId).isEqualTo(hankeGeometriat.createdByUserId)
            assertThat(loadedHankeGeometriat!!.updatedByUserId).isEqualTo(hankeGeometriat.updatedByUserId)
            assertThat(loadedHankeGeometriat!!.featureCollection!!.features.size).isEqualTo(2)
            assertThat(loadedHankeGeometriat!!.featureCollection!!.features[0].geometry is Point)
            val loadedPoint = loadedHankeGeometriat!!.featureCollection!!.features[0].geometry as Point
            val point = hankeGeometriat.featureCollection!!.features[0].geometry as Point
            assertThat(loadedPoint.coordinates).isEqualTo(point.coordinates)
        }

        // update
        loadedHankeGeometriat.featureCollection!!.features.add(loadedHankeGeometriat.featureCollection!!.features[0])
        loadedHankeGeometriat.id = null
        loadedHankeGeometriat.version = null
        loadedHankeGeometriat.updatedAt = null

        // save
        hankeGeometriatService.saveGeometriat(hankeTunnus, loadedHankeGeometriat)

        // load
        loadedHankeGeometriat = hankeGeometriatService.loadGeometriat(hankeTunnus)
        assertAll {
            assertThat(loadedHankeGeometriat!!.hankeId).isEqualTo(hankeGeometriat.hankeId)
            assertThat(loadedHankeGeometriat.version).isEqualTo(2) // this has increased
            assertThat(loadedHankeGeometriat.createdByUserId).isEqualTo(hankeGeometriat.createdByUserId)
            assertThat(loadedHankeGeometriat.createdAt!!.format(DATABASE_TIMESTAMP_FORMAT)).isEqualTo(createdAt.format(DATABASE_TIMESTAMP_FORMAT))
            assertThat(loadedHankeGeometriat.updatedByUserId).isEqualTo(hankeGeometriat.updatedByUserId)
            assertThat(loadedHankeGeometriat.featureCollection!!.features.size).isEqualTo(3) // this was increased
            assertThat(loadedHankeGeometriat.featureCollection!!.features[0].geometry is Point)
            val loadedPoint = loadedHankeGeometriat.featureCollection!!.features[0].geometry as Point
            val point = hankeGeometriat.featureCollection!!.features[0].geometry as Point
            assertThat(loadedPoint.coordinates).isEqualTo(point.coordinates)
        }

        // check database too to make sure there is everything correctly
        assertAll {
            assertThat(jdbcOperations.queryForObject("SELECT COUNT(*) FROM HankeGeometriat") { rs, _ -> rs.getInt(1) }).isEqualTo(1)
            val ids = jdbcOperations.query("SELECT id, hankegeometriatid FROM HankeGeometria") { rs, _ ->
                Pair(rs.getInt(1), rs.getInt(2))
            }
            assertThat(ids.size).isEqualTo(3)
            ids.forEach { idPair ->
                assertThat(idPair.first).isNotNull()
                assertThat(idPair.second).isEqualTo(loadedHankeGeometriat!!.id)
            }
        }
    }
}