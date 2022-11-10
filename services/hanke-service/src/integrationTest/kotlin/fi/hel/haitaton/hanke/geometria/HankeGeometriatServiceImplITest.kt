package fi.hel.haitaton.hanke.geometria

import assertk.assertAll
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import fi.hel.haitaton.hanke.DATABASE_TIMESTAMP_FORMAT
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.countMapper
import fi.hel.haitaton.hanke.domain.Hanke
import org.geojson.Point
import org.junit.jupiter.api.Test
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

    @Test
    fun `save and load and update`() {
        val hankeGeometriat: HankeGeometriat =
            "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json".asJsonResource()
        val username = SecurityContextHolder.getContext().authentication.name

        // For FK constraints we need a Hanke in database
        // Using hankeService to create the dummy hanke into database causes
        // tunnus and id to be whatever the service thinks is right, so
        // they must be picked from the created hanke-instance.
        val hanke = hankeService.createHanke(Hanke(hankeGeometriat.hankeId!!, ""))
        val hankeTunnus = hanke.hankeTunnus!!
        hankeGeometriat.hankeId = hanke.id // replaces the id with the correct one

        // save
        hankeGeometriatService.saveGeometriat(hanke, hankeGeometriat)

        // NOTE: the local Hanke instance has not been updated by the above call. Need to reload
        // the hanke to check that the flag changed to true:
        val updatedHanke = hankeService.loadHanke(hankeTunnus)
        assertThat(updatedHanke).isNotNull()

        // load
        var loadedHankeGeometriat = hankeGeometriatService.loadGeometriat(hanke)
        val createdAt = loadedHankeGeometriat!!.createdAt!!
        val modifiedAt = loadedHankeGeometriat.modifiedAt!!
        assertAll {
            assertThat(loadedHankeGeometriat!!.hankeId).isEqualTo(hankeGeometriat.hankeId)
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
                    loadedHankeGeometriat?.featureCollection!!.features[0].properties["hankeTunnus"]
                )
                .isEqualTo(hankeTunnus)
        }

        // update
        val first = loadedHankeGeometriat.featureCollection!!.features[0]
        loadedHankeGeometriat.featureCollection!!.features.add(first)
        loadedHankeGeometriat.id = null
        loadedHankeGeometriat.version = null
        loadedHankeGeometriat.modifiedAt = null

        // save
        hankeGeometriatService.saveGeometriat(hanke, loadedHankeGeometriat)

        // load
        loadedHankeGeometriat = hankeGeometriatService.loadGeometriat(hanke)
        assertAll {
            assertThat(loadedHankeGeometriat!!.hankeId).isEqualTo(hankeGeometriat.hankeId)
            assertThat(loadedHankeGeometriat.version).isEqualTo(1) // this has increased
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
                    jdbcTemplate.queryForObject("SELECT COUNT(*) FROM HankeGeometriat", countMapper)
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
}
