package fi.hel.haitaton.hanke.geometria

import assertk.assertAll
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.countMapper
import java.time.ZonedDateTime
import javax.transaction.Transactional
import org.geojson.Point
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
@Transactional
internal class HankeGeometriatDaoImplITest : DatabaseTest() {

    @Autowired private lateinit var hankeGeometriatDao: HankeGeometriatDao

    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `CRUD testing`() {
        val hankeGeometriat: HankeGeometriat =
            "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json".asJsonResource()
        hankeGeometriat.createdByUserId = "1111"
        hankeGeometriat.modifiedByUserId = "2222"
        // For FK constraints we need a Hanke in database
        jdbcTemplate.execute("INSERT INTO Hanke (id) VALUES (${hankeGeometriat.hankeId})")

        // Create
        hankeGeometriatDao.createHankeGeometriat(hankeGeometriat)
        // Retrieve
        var loadedHankeGeometriat =
            hankeGeometriatDao.retrieveHankeGeometriat(hankeGeometriat.hankeId!!)
        assertAll {
            assertThat(loadedHankeGeometriat!!.hankeId).isEqualTo(hankeGeometriat.hankeId)
            assertThat(loadedHankeGeometriat!!.version).isEqualTo(hankeGeometriat.version)
            assertThat(loadedHankeGeometriat!!.createdByUserId)
                .isEqualTo(hankeGeometriat.createdByUserId)
            assertThat(loadedHankeGeometriat!!.createdAt).isEqualTo(hankeGeometriat.createdAt)
            assertThat(loadedHankeGeometriat!!.modifiedByUserId)
                .isEqualTo(hankeGeometriat.modifiedByUserId)
            assertThat(loadedHankeGeometriat!!.modifiedAt).isEqualTo(hankeGeometriat.modifiedAt)
            assertThat(loadedHankeGeometriat!!.featureCollection!!.features.size).isEqualTo(2)
            assertThat(loadedHankeGeometriat!!.featureCollection!!.features[0].geometry is Point)
            val loadedPoint =
                loadedHankeGeometriat!!.featureCollection!!.features[0].geometry as Point
            val point = hankeGeometriat.featureCollection!!.features[0].geometry as Point
            assertThat(loadedPoint.coordinates).isEqualTo(point.coordinates)
        }
        // Update
        hankeGeometriat.featureCollection!!
            .features
            .add(hankeGeometriat.featureCollection!!.features[0]) // add one more geometry
        hankeGeometriat.version = hankeGeometriat.version!! + 1
        hankeGeometriat.modifiedAt = ZonedDateTime.now()
        hankeGeometriatDao.updateHankeGeometriat(hankeGeometriat)
        loadedHankeGeometriat =
            hankeGeometriatDao.retrieveHankeGeometriat(hankeGeometriat.hankeId!!)
        assertAll {
            assertThat(loadedHankeGeometriat!!.hankeId).isEqualTo(hankeGeometriat.hankeId)
            assertThat(loadedHankeGeometriat.version).isEqualTo(hankeGeometriat.version)
            assertThat(loadedHankeGeometriat.createdByUserId)
                .isEqualTo(hankeGeometriat.createdByUserId)
            assertThat(loadedHankeGeometriat.createdAt).isEqualTo(hankeGeometriat.createdAt)
            assertThat(loadedHankeGeometriat.modifiedByUserId)
                .isEqualTo(hankeGeometriat.modifiedByUserId)
            assertThat(loadedHankeGeometriat.modifiedAt!!.isAfter(hankeGeometriat.modifiedAt!!))
            assertThat(loadedHankeGeometriat.featureCollection!!.features.size)
                .isEqualTo(3) // this has increased
            assertThat(loadedHankeGeometriat.featureCollection!!.features[0].geometry is Point)
            val loadedPoint =
                loadedHankeGeometriat.featureCollection!!.features[0].geometry as Point
            val point = hankeGeometriat.featureCollection!!.features[0].geometry as Point
            assertThat(loadedPoint.coordinates).isEqualTo(point.coordinates)
        }

        // Delete
        hankeGeometriatDao.deleteHankeGeometriat(hankeGeometriat)
        // check that all was deleted correctly
        assertThat(hankeGeometriatDao.retrieveHankeGeometriat(hankeGeometriat.hankeId!!))
            .isNotNull()
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM HankeGeometriat", countMapper))
            .isEqualTo(1)
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM HankeGeometria", countMapper))
            .isEqualTo(0)
    }
}
