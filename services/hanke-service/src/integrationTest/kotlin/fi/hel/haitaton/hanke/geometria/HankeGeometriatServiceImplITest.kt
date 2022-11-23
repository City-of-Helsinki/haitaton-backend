package fi.hel.haitaton.hanke.geometria

import assertk.assertAll
import assertk.assertThat
import assertk.assertions.*
import fi.hel.haitaton.hanke.*
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.factory.HankealueFactory
import org.geojson.Point
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
@Transactional
@WithMockUser(username = "test", roles = ["haitaton-user"])
internal class HankeGeometriatServiceImplITest : DatabaseTest() {

    @Autowired private lateinit var hankeService: HankeService

    @Autowired private lateinit var hankeGeometriatService: HankeGeometriatService

    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        // delete existing data from database
        jdbcTemplate.execute("DELETE FROM HankeGeometriat")
    }

    @Test
    fun `save and load and update`() {
        val hankeGeometriat =
            "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json".asJsonResource(
                HankeGeometriat::class.java
            )
        val username = SecurityContextHolder.getContext().authentication.name

        // For FK constraints we need a Hanke in database
        // Using hankeService to create the dummy hanke into database causes
        // tunnus and id to be whatever the service thinks is right, so
        // they must be picked from the created hanke-instance.
        val hankeId = 1
        val hankeInit = Hanke(hankeId)
        hankeInit.alueet.add(HankealueFactory.create(null, null, geometriat = hankeGeometriat))
        val hanke = hankeService.createHanke(hankeInit)
        val hankeTunnus = hanke.hankeTunnus!!

        // NOTE: the local Hanke instance has not been updated by the above call. Need to reload
        // the hanke to check that the flag changed to true:
        val updatedHanke = hankeService.loadHanke(hankeTunnus)
        assertThat(updatedHanke).isNotNull()
        assertThat(updatedHanke!!.alueidenGeometriat()).isNotEmpty()

        // loading geometries
        var loadedHankeGeometriat =
            hankeGeometriatService.getGeometriat(updatedHanke.alueidenGeometriat().get(0).id!!)
        assertThat(loadedHankeGeometriat).isNotNull()

        val createdAt = loadedHankeGeometriat!!.createdAt!!
        val modifiedAt = loadedHankeGeometriat.modifiedAt!!
        assertAll {
            assertThat(loadedHankeGeometriat!!.version).isEqualTo(0)
            assertThat(loadedHankeGeometriat!!.createdByUserId).isEqualTo(username)
            assertThat(loadedHankeGeometriat!!.modifiedByUserId).isNull()
            assertThat(loadedHankeGeometriat!!.featureCollection!!.features.size).isEqualTo(2)
            assertThat(loadedHankeGeometriat!!.featureCollection!!.features[0].geometry is Point)
            val loadedPoint =
                loadedHankeGeometriat!!.featureCollection!!.features[0].geometry as Point
            val point = hankeGeometriat.featureCollection!!.features[0].geometry as Point
            assertThat(loadedPoint.coordinates).isEqualTo(point.coordinates)
            assertThat(
                    loadedHankeGeometriat!!
                        .featureCollection!!
                        .features[0]
                        .properties["hankeTunnus"]
                )
                .isEqualTo(hankeTunnus)
        }

        // update geometriat
        loadedHankeGeometriat.featureCollection!!
            .features
            .add(loadedHankeGeometriat.featureCollection!!.features[0])
        hankeGeometriatService.saveGeometriat(loadedHankeGeometriat)

        // load
        loadedHankeGeometriat = hankeGeometriatService.getGeometriat(loadedHankeGeometriat.id!!)
        assertAll {
            assertThat(loadedHankeGeometriat!!.version).isEqualTo(1) // this has increased
            assertThat(loadedHankeGeometriat.createdByUserId).isEqualTo(username)
            assertThat(loadedHankeGeometriat.createdAt!!.format(DATABASE_TIMESTAMP_FORMAT))
                .isEqualTo(createdAt.format(DATABASE_TIMESTAMP_FORMAT))
            assertThat(loadedHankeGeometriat.modifiedAt!!.isAfter(modifiedAt)) // this has changed
            assertThat(loadedHankeGeometriat.modifiedByUserId).isEqualTo(username)
            assertThat(loadedHankeGeometriat.featureCollection!!.features.size)
                .isEqualTo(3) // this has increased
            assertThat(loadedHankeGeometriat.featureCollection!!.features[0].geometry is Point)
            val loadedPoint =
                loadedHankeGeometriat.featureCollection!!.features[0].geometry as Point
            val point = hankeGeometriat.featureCollection!!.features[0].geometry as Point
            assertThat(loadedPoint.coordinates).isEqualTo(point.coordinates)
            assertThat(
                    loadedHankeGeometriat.featureCollection!!.features[0].properties["hankeTunnus"]
                )
                .isEqualTo(hankeTunnus)
        }

        // check database too to make sure there is everything correctly
        assertAll {
            assertThat(
                    jdbcTemplate.queryForObject("SELECT COUNT(*) FROM HankeGeometriat") { rs, _ ->
                        rs.getInt(1)
                    }
                )
                .isEqualTo(1)
            val ids =
                jdbcTemplate.query("SELECT id, hankegeometriatid FROM HankeGeometria") { rs, _ ->
                    Pair(rs.getInt(1), rs.getInt(2))
                }
            assertThat(ids.size).isEqualTo(3)
            ids.forEach { idPair ->
                assertThat(idPair.first).isNotNull()
                assertThat(idPair.second).isEqualTo(loadedHankeGeometriat!!.id)
            }
        }
    }

    @Test
    fun `save Geometria with invalid coordinate system`() {
        val hankeGeometriat =
            "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json".asJsonResource(
                HankeGeometriat::class.java
            )
        hankeGeometriat.version = null
        hankeGeometriat.createdAt = null
        hankeGeometriat.modifiedAt = null
        hankeGeometriat.featureCollection
            ?.crs
            ?.properties
            ?.set("name", "urn:ogc:def:crs:EPSG::0000")

        assertThrows<GeometriaValidationException> {
            hankeGeometriatService.saveGeometriat(hankeGeometriat)
        }
    }
}
