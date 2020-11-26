package fi.hel.haitaton.hanke.geometria

import assertk.assertAll
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import fi.hel.haitaton.hanke.HaitatonPostgreSQLContainer
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.OBJECT_MAPPER
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
import javax.transaction.Transactional


@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
@Transactional
internal class HankeGeometriatDaoImplITest {

    companion object {
        @Container
        var container: HaitatonPostgreSQLContainer = HaitatonPostgreSQLContainer
                .withPassword("test")
                .withUsername("test")
                .withExposedPorts(5433)

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
    private lateinit var jdbcOperations: JdbcOperations

    @BeforeEach
    fun setUp() {
        // delete all
        jdbcOperations.execute("DELETE FROM HankeGeometriat")
    }

    @Test
    fun `save, load and delete`() {
        val hankeGeometriat = OBJECT_MAPPER.readValue(Files.readString(Paths.get("src/integrationTest/resources/fi/hel/haitaton/hanke/hankeGeometriat.json")), HankeGeometriat::class.java)
        hankeGeometriat.createdByUserId = 1111
        hankeGeometriat.updatedByUserId = 2222
        // For FK constraints we need a Hanke in database
        hankeRepository.save(HankeEntity(id = hankeGeometriat.hankeId))

        // save
        hankeGeometriatDao.saveHankeGeometriat(hankeGeometriat)
        // load
        val loadedHankeGeometriat = hankeGeometriatDao.loadHankeGeometriat(hankeGeometriat.hankeId!!)
        // check results
        assertAll {
            assertThat(loadedHankeGeometriat!!.hankeId).isEqualTo(hankeGeometriat.hankeId)
            assertThat(loadedHankeGeometriat.version).isEqualTo(hankeGeometriat.version)
            assertThat(loadedHankeGeometriat.createdByUserId).isEqualTo(hankeGeometriat.createdByUserId)
            assertThat(loadedHankeGeometriat.createdAt).isEqualTo(hankeGeometriat.createdAt)
            assertThat(loadedHankeGeometriat.updatedByUserId).isEqualTo(hankeGeometriat.updatedByUserId)
            assertThat(loadedHankeGeometriat.updatedAt).isEqualTo(hankeGeometriat.updatedAt)
            assertThat(loadedHankeGeometriat.featureCollection!!.features.size).isEqualTo(hankeGeometriat.featureCollection!!.features.size)
            assertThat(loadedHankeGeometriat.featureCollection!!.features[0].geometry is Point)
            val loadedPoint = loadedHankeGeometriat.featureCollection!!.features[0].geometry as Point
            val point = hankeGeometriat.featureCollection!!.features[0].geometry as Point
            assertThat(loadedPoint.coordinates).isEqualTo(point.coordinates)
        }

        // delete
        hankeGeometriatDao.deleteHankeGeometriat(hankeGeometriat.hankeId!!)
        // check that all was deleted correctly
        assertThat(hankeGeometriatDao.loadHankeGeometriat(hankeGeometriat.hankeId!!)).isNull()
        assertThat(jdbcOperations.queryForObject("SELECT COUNT(*) FROM HankeGeometriat") { rs, _ -> rs.getInt(1) }).isEqualTo(0)
        assertThat(jdbcOperations.queryForObject("SELECT COUNT(*) FROM HankeGeometria") { rs, _ -> rs.getInt(1) }).isEqualTo(0)
    }
}