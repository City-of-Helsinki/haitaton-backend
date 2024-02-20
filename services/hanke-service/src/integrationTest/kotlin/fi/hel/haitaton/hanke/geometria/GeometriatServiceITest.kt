package fi.hel.haitaton.hanke.geometria

import assertk.all
import assertk.assertAll
import assertk.assertThat
import assertk.assertions.extracting
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.prop
import fi.hel.haitaton.hanke.DATABASE_TIMESTAMP_FORMAT
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.domain.geometriat
import fi.hel.haitaton.hanke.factory.GeometriaFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankealueFactory
import fi.hel.haitaton.hanke.test.Asserts.isRecentZDT
import org.geojson.Polygon
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(username = "test")
internal class GeometriatServiceITest : DatabaseTest() {

    @Autowired private lateinit var hankeService: HankeService

    @Autowired private lateinit var geometriatService: GeometriatService

    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired private lateinit var hankeFactory: HankeFactory

    @Test
    fun `save and load and update`() {
        val geometriat: Geometriat = GeometriaFactory.create()
        val username = SecurityContextHolder.getContext().authentication.name

        // For FK constraints we need a Hanke in database
        // Using hankeService to create the dummy hanke into database causes
        // tunnus and id to be whatever the service thinks is right, so
        // they must be picked from the created hanke-instance.
        val hankeTunnus =
            hankeFactory
                .builder("test")
                .withHankealue(HankealueFactory.createMinimal(geometriat = geometriat))
                .save()
                .hankeTunnus

        // NOTE: the local Hanke instance has not been updated by the above call. Need to reload
        // the hanke to check that the flag changed to true:
        val updatedHanke = hankeService.loadHanke(hankeTunnus)
        assertThat(updatedHanke).isNotNull()
        assertThat(updatedHanke!!.alueet.geometriat()).isNotEmpty()

        // loading geometries
        var loadedGeometriat =
            geometriatService.getGeometriat(updatedHanke.alueet.geometriat()[0].id!!)
        assertThat(loadedGeometriat).isNotNull()

        val createdAt = loadedGeometriat!!.createdAt!!
        assertAll {
            assertThat(loadedGeometriat!!.version).isEqualTo(0)
            assertThat(loadedGeometriat!!.createdByUserId).isEqualTo(username)
            assertThat(loadedGeometriat!!.modifiedByUserId).isNull()
            assertThat(loadedGeometriat!!.modifiedAt).isNull()
            assertThat(loadedGeometriat!!.featureCollection!!.features.size).isEqualTo(1)
            val loadedPolygon =
                loadedGeometriat!!.featureCollection!!.features[0].geometry as Polygon
            val polygon = geometriat.featureCollection!!.features[0].geometry as Polygon
            assertThat(loadedPolygon.coordinates).isEqualTo(polygon.coordinates)
            assertThat(loadedGeometriat!!.featureCollection!!.features[0].properties["hankeTunnus"])
                .isEqualTo(hankeTunnus)
        }

        // update geometriat
        loadedGeometriat.featureCollection!!
            .features
            .add(loadedGeometriat.featureCollection!!.features[0])
        geometriatService.saveGeometriat(loadedGeometriat, loadedGeometriat.id)

        // load
        loadedGeometriat = geometriatService.getGeometriat(loadedGeometriat.id!!)
        assertAll {
            assertThat(loadedGeometriat!!.version).isEqualTo(1) // this has increased
            assertThat(loadedGeometriat.createdByUserId).isEqualTo(username)
            assertThat(loadedGeometriat.createdAt!!.format(DATABASE_TIMESTAMP_FORMAT))
                .isEqualTo(createdAt.format(DATABASE_TIMESTAMP_FORMAT))
            assertThat(loadedGeometriat.modifiedAt!!).isNotNull() // this has changed
            assertThat(loadedGeometriat.modifiedByUserId).isEqualTo(username)
            assertThat(loadedGeometriat.featureCollection!!.features.size)
                .isEqualTo(2) // this has increased
            assertThat(loadedGeometriat.featureCollection!!.features[0].geometry is Polygon)
            val loadedPolygon = loadedGeometriat.featureCollection!!.features[0].geometry as Polygon
            val polygon = geometriat.featureCollection!!.features[0].geometry as Polygon
            assertThat(loadedPolygon.coordinates).isEqualTo(polygon.coordinates)
            assertThat(loadedGeometriat.featureCollection!!.features[0].properties["hankeTunnus"])
                .isEqualTo(hankeTunnus)
        }

        // check database too to make sure there is everything correctly
        assertAll {
            assertThat(getGeometriaCount()).isEqualTo(1)
            val ids =
                jdbcTemplate.query("SELECT id, hankegeometriatid FROM HankeGeometria") { rs, _ ->
                    Pair(rs.getInt(1), rs.getInt(2))
                }
            assertThat(ids.size).isEqualTo(2)
            ids.forEach { idPair ->
                assertThat(idPair.first).isNotNull()
                assertThat(idPair.second).isEqualTo(loadedGeometriat!!.id)
            }
        }
    }

    @Test
    fun `save Geometria with missing properties`() {
        val geometriat = GeometriaFactory.createNew()
        geometriat.featureCollection?.crs?.properties = null

        assertThrows<GeometriaValidationException> {
            geometriatService.saveGeometriat(geometriat, null)
        }
    }

    @Test
    fun `save Geometria with invalid coordinate system`() {
        val geometriat = GeometriaFactory.createNew()
        geometriat.featureCollection?.crs?.properties?.set("name", "urn:ogc:def:crs:EPSG::0000")

        assertThrows<UnsupportedCoordinateSystemException> {
            geometriatService.saveGeometriat(geometriat, null)
        }
    }

    @Nested
    inner class CreateGeometria {
        @Test
        fun `sets metadata correctly`() {
            val geometriat = GeometriaFactory.createNew()

            val result = geometriatService.createGeometriat(geometriat)

            val savedGeometriat = geometriatService.getGeometriat(result.id!!)
            assertThat(savedGeometriat).isNotNull().all {
                prop(Geometriat::version).isEqualTo(0)
                prop(Geometriat::createdAt).isRecentZDT()
                prop(Geometriat::createdByUserId).isEqualTo("test")
                prop(Geometriat::modifiedAt).isNull()
                prop(Geometriat::modifiedByUserId).isNull()
                prop(Geometriat::featureCollection).isNotNull().all {
                    transform("features") { it.features }.isNotEmpty()
                }
                isNotSameInstanceAs(geometriat)
            }
        }

        @Test
        fun `saves geometries correctly`() {
            val geometriat: Geometriat = GeometriaFactory.create()

            val result = geometriatService.createGeometriat(geometriat)

            val savedGeometriat = geometriatService.getGeometriat(result.id!!)
            assertThat(savedGeometriat).isNotNull().all {
                isNotSameInstanceAs(geometriat)
                transform("polygons") { getPolygons(it) }
                    .extracting(Polygon::getCoordinates)
                    .isEqualTo(getPolygons(geometriat).map { it.coordinates })
            }
        }
    }

    private fun getPolygons(geometriat: Geometriat): List<Polygon> =
        geometriat.featureCollection!!.features.map { it.geometry as Polygon }

    private fun getGeometriaCount(): Int? =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM geometriat") { rs, _ -> rs.getInt(1) }
}
