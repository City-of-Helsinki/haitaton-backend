package fi.hel.haitaton.hanke.geometria

import assertk.assertAll
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import fi.hel.haitaton.hanke.HaitatonPostgreSQLContainer
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.asJsonResource
import org.geojson.Point
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.transaction.Transactional

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
@Transactional
internal class HankeGeometriatDaoImplITest {

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
    private lateinit var hankeGeometriatDao: HankeGeometriatDao

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `CRUD testing`() {
        val hankeGeometriat = "/fi/hel/haitaton/hanke/hankeGeometriat.json".asJsonResource(HankeGeometriat::class.java)
        hankeGeometriat.createdByUserId = 1111
        hankeGeometriat.modifiedByUserId = 2222
        // For FK constraints we need a Hanke in database
        hankeRepository.save(HankeEntity(id = hankeGeometriat.hankeId))

        // Create
        hankeGeometriatDao.createHankeGeometriat(hankeGeometriat)
        // Retrieve
        var loadedHankeGeometriat = hankeGeometriatDao.retrieveHankeGeometriat(hankeGeometriat.hankeId!!)
        assertAll {
            assertThat(loadedHankeGeometriat!!.hankeId).isEqualTo(hankeGeometriat.hankeId)
            assertThat(loadedHankeGeometriat!!.version).isEqualTo(hankeGeometriat.version)
            assertThat(loadedHankeGeometriat!!.createdByUserId).isEqualTo(hankeGeometriat.createdByUserId)
            assertThat(loadedHankeGeometriat!!.createdAt).isEqualTo(hankeGeometriat.createdAt)
            assertThat(loadedHankeGeometriat!!.modifiedByUserId).isEqualTo(hankeGeometriat.modifiedByUserId)
            assertThat(loadedHankeGeometriat!!.modifiedAt).isEqualTo(hankeGeometriat.modifiedAt)
            assertThat(loadedHankeGeometriat!!.featureCollection!!.features.size).isEqualTo(2)
            assertThat(loadedHankeGeometriat!!.featureCollection!!.features[0].geometry is Point)
            val loadedPoint = loadedHankeGeometriat!!.featureCollection!!.features[0].geometry as Point
            val point = hankeGeometriat.featureCollection!!.features[0].geometry as Point
            assertThat(loadedPoint.coordinates).isEqualTo(point.coordinates)
        }
        // Update
        hankeGeometriat.featureCollection!!.features.add(hankeGeometriat.featureCollection!!.features[0]) // add one more geometry
        hankeGeometriat.version = hankeGeometriat.version!! + 1
        hankeGeometriat.modifiedAt = ZonedDateTime.now()
        hankeGeometriatDao.updateHankeGeometriat(hankeGeometriat)
        loadedHankeGeometriat = hankeGeometriatDao.retrieveHankeGeometriat(hankeGeometriat.hankeId!!)
        assertAll {
            assertThat(loadedHankeGeometriat!!.hankeId).isEqualTo(hankeGeometriat.hankeId)
            assertThat(loadedHankeGeometriat.version).isEqualTo(hankeGeometriat.version)
            assertThat(loadedHankeGeometriat.createdByUserId).isEqualTo(hankeGeometriat.createdByUserId)
            assertThat(loadedHankeGeometriat.createdAt).isEqualTo(hankeGeometriat.createdAt)
            assertThat(loadedHankeGeometriat.modifiedByUserId).isEqualTo(hankeGeometriat.modifiedByUserId)
            assertThat(loadedHankeGeometriat.modifiedAt!!.isAfter(hankeGeometriat.modifiedAt!!))
            assertThat(loadedHankeGeometriat.featureCollection!!.features.size).isEqualTo(3) // this has increased
            assertThat(loadedHankeGeometriat.featureCollection!!.features[0].geometry is Point)
            val loadedPoint = loadedHankeGeometriat.featureCollection!!.features[0].geometry as Point
            val point = hankeGeometriat.featureCollection!!.features[0].geometry as Point
            assertThat(loadedPoint.coordinates).isEqualTo(point.coordinates)
        }

        // Delete
        jdbcTemplate.execute("DELETE FROM HankeGeometriat WHERE hankeId=${hankeGeometriat.hankeId}")
        // check that all was deleted correctly
        assertThat(hankeGeometriatDao.retrieveHankeGeometriat(hankeGeometriat.hankeId!!)).isNull()
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM HankeGeometriat") { rs, _ -> rs.getInt(1) }).isEqualTo(0)
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM HankeGeometria") { rs, _ -> rs.getInt(1) }).isEqualTo(0)
    }
}