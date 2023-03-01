package fi.hel.haitaton.hanke.geometria

import assertk.assertAll
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.asJsonResource
import java.time.ZonedDateTime
import javax.transaction.Transactional
import org.geojson.Point
import org.geojson.Polygon
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
internal class GeometriatDaoImplITest : DatabaseTest() {

    private val expectedPolygonArea = 1707f

    @Autowired private lateinit var geometriatDao: GeometriatDao

    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `CRUD testing`() {
        val hankeGeometriat =
            "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json".asJsonResource(
                Geometriat::class.java
            )
        hankeGeometriat.createdByUserId = "1111"
        hankeGeometriat.modifiedByUserId = "2222"

        // Create
        val geometriat = geometriatDao.createGeometriat(hankeGeometriat)
        val geometriaId = geometriat.id!!

        // Retrieve
        var loadedGeometriat = geometriatDao.retrieveGeometriat(geometriaId)

        assertAll {
            assertThat(loadedGeometriat!!.version).isEqualTo(geometriat.version)
            assertThat(loadedGeometriat!!.createdByUserId).isEqualTo(geometriat.createdByUserId)
            assertThat(loadedGeometriat!!.createdAt).isEqualTo(geometriat.createdAt)
            assertThat(loadedGeometriat!!.modifiedByUserId).isEqualTo(geometriat.modifiedByUserId)
            assertThat(loadedGeometriat!!.modifiedAt).isEqualTo(geometriat.modifiedAt)
            assertThat(loadedGeometriat!!.featureCollection!!.features.size).isEqualTo(2)
            assertThat(loadedGeometriat!!.featureCollection!!.features[0].geometry is Point)
            val loadedPoint = loadedGeometriat!!.featureCollection!!.features[0].geometry as Point
            val point = geometriat.featureCollection!!.features[0].geometry as Point
            assertThat(loadedPoint.coordinates).isEqualTo(point.coordinates)
        }
        // Update
        geometriat.featureCollection!!
            .features
            .add(geometriat.featureCollection!!.features[0]) // add one more geometry
        geometriat.version = geometriat.version!! + 1
        geometriat.modifiedAt = ZonedDateTime.now()
        geometriatDao.updateGeometriat(geometriat)
        loadedGeometriat = geometriatDao.retrieveGeometriat(geometriaId)
        assertAll {
            assertThat(geometriaId).isEqualTo(geometriaId)
            assertThat(loadedGeometriat!!.version).isEqualTo(geometriat.version)
            assertThat(loadedGeometriat.createdByUserId).isEqualTo(geometriat.createdByUserId)
            assertThat(loadedGeometriat.createdAt).isEqualTo(geometriat.createdAt)
            assertThat(loadedGeometriat.modifiedByUserId).isEqualTo(geometriat.modifiedByUserId)
            assertThat(loadedGeometriat.modifiedAt!!.isAfter(geometriat.modifiedAt!!))
            assertThat(loadedGeometriat.featureCollection!!.features.size)
                .isEqualTo(3) // this has increased
            assertThat(loadedGeometriat.featureCollection!!.features[0].geometry is Point)
            val loadedPoint = loadedGeometriat.featureCollection!!.features[0].geometry as Point
            val point = geometriat.featureCollection!!.features[0].geometry as Point
            assertThat(loadedPoint.coordinates).isEqualTo(point.coordinates)
        }

        // Delete
        geometriatDao.deleteGeometriat(geometriat)
        // check that all was deleted correctly
        assertThat(geometriatDao.retrieveGeometriat(geometriaId)).isNull()
        assertThat(getGeometriaCount()).isEqualTo(0)
        assertThat(getHankeGeometriaCount()).isEqualTo(0)
    }

    @Test
    fun `calculateArea correctly calculates the area of a polygon`() {
        val polygon: Polygon = "/fi/hel/haitaton/hanke/geometria/polygon.json".asJsonResource()

        val result = geometriatDao.calculateArea(polygon)!!

        assertThat(result).isNotNull().isCloseTo(expectedPolygonArea, .1f)
    }

    @Test
    fun `calculateCombinedArea returns 0 for empty list`() {
        val result = geometriatDao.calculateCombinedArea(listOf())

        assertThat(result).isEqualTo(0f)
    }

    @Test
    fun `calculateCombinedArea counts overlapping areas only once`() {
        val polygon: Polygon = "/fi/hel/haitaton/hanke/geometria/polygon.json".asJsonResource()

        val result = geometriatDao.calculateCombinedArea(listOf(polygon, polygon))

        assertThat(result).isNotNull().isCloseTo(expectedPolygonArea, .1f)
    }

    private fun getGeometriaCount(): Int? =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM geometriat", Int::class.java)
    private fun getHankeGeometriaCount(): Int? =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM HankeGeometria", Int::class.java)
}
