package fi.hel.haitaton.hanke.geometria

import assertk.assertAll
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isCloseTo
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.factory.GeometriaFactory
import java.time.ZonedDateTime
import org.geojson.Polygon
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.jdbc.Sql

internal class GeometriatDaoITest : IntegrationTest() {

    private val expectedPolygonArea = 1707f

    @Autowired private lateinit var geometriatDao: GeometriatDao

    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `CRUD testing`() {
        val hankeGeometriat = GeometriaFactory.create()
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
            assertThat(loadedGeometriat!!.featureCollection!!.features.size).isEqualTo(1)
            val loadedPolygon =
                loadedGeometriat!!.featureCollection!!.features[0].geometry as Polygon
            val polygon = geometriat.featureCollection!!.features[0].geometry as Polygon
            assertThat(loadedPolygon.coordinates).isEqualTo(polygon.coordinates)
        }
        // Update
        geometriat.featureCollection!!
            .features
            .add(geometriat.featureCollection!!.features[0]) // add one more geometry
        geometriat.version += 1
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
                .isEqualTo(2) // this has increased
            val loadedPolygon = loadedGeometriat.featureCollection!!.features[0].geometry as Polygon
            val polygon = geometriat.featureCollection!!.features[0].geometry as Polygon
            assertThat(loadedPolygon.coordinates).isEqualTo(polygon.coordinates)
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

    @Test
    fun `matchingHankealueet returns empty if there's no hanke with that id`() {
        val result = geometriatDao.matchingHankealueet(5, aleksanterinPatsas)

        assertThat(result).isEmpty()
    }

    @Test
    @Sql("/sql/alueeton-hanke.sql")
    fun `matchingHankealueet returns empty if hanke has no alueet`() {
        val result = geometriatDao.matchingHankealueet(5, aleksanterinPatsas)

        assertThat(result).isEmpty()
    }

    @Test
    @Sql("/sql/senaatintorin-hanke.sql")
    fun `matchingHankealueet returns hankealue id when the object is inside a hanke alue`() {
        val result = geometriatDao.matchingHankealueet(5, aleksanterinPatsas)

        assertThat(result).containsExactly(23)
    }

    @Test
    @Sql("/sql/senaatintorin-hanke.sql")
    fun `matchingHankealueet returns empty when the object is outside all hanke alueet`() {
        val result = geometriatDao.matchingHankealueet(5, havisAmanda)

        assertThat(result).isEmpty()
    }

    @Test
    @Sql("/sql/senaatintorin-hanke.sql")
    fun `matchingHankealueet returns empty when the object is partly outside every hanke alue`() {
        val result = geometriatDao.matchingHankealueet(5, tuomiokirkonPortaat)

        assertThat(result).isEmpty()
    }

    @Test
    @Sql("/sql/senaatintorin-hanke.sql")
    fun `matchingHankealueet returns hankealue id when the object perfectly matches a hanke alue`() {
        val result = geometriatDao.matchingHankealueet(5, senaatintori)

        assertThat(result).containsExactly(23)
    }

    @Test
    @Sql("/sql/monen-alueen-hanke.sql")
    fun `matchingHankealueet returns all matching hankealue id when there are several hankealue`() {
        val result = geometriatDao.matchingHankealueet(5, aleksanterinPatsas)

        assertThat(result).containsExactly(23, 24)
    }

    private fun getGeometriaCount(): Int? =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM geometriat", Int::class.java)

    private fun getHankeGeometriaCount(): Int? =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM HankeGeometria", Int::class.java)

    private val senaatintori: Polygon =
        "/fi/hel/haitaton/hanke/geometria/senaatintori.json".asJsonResource()

    private val tuomiokirkonPortaat: Polygon =
        "/fi/hel/haitaton/hanke/geometria/tuomiokirkon-portaat.json".asJsonResource()

    private val havisAmanda: Polygon =
        "/fi/hel/haitaton/hanke/geometria/havis-amanda.json".asJsonResource()

    private val aleksanterinPatsas: Polygon =
        "/fi/hel/haitaton/hanke/geometria/aleksanterin-patsas.json".asJsonResource()
}
