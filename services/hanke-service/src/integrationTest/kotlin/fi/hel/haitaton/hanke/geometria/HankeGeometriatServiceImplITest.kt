package fi.hel.haitaton.hanke.geometria

import assertk.assertAll
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import fi.hel.haitaton.hanke.DATABASE_TIMESTAMP_FORMAT
import fi.hel.haitaton.hanke.HaitatonPostgreSQLContainer
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.domain.Hanke
import org.geojson.Point
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
@Transactional
@WithMockUser(username = "test", roles = ["haitaton-user"])
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
    private lateinit var hankeService: HankeService

    @Autowired
    private lateinit var hankeGeometriatService: HankeGeometriatService

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        // delete existing data from database
        jdbcTemplate.execute("DELETE FROM HankeGeometriat")
    }

    @Test
    fun `save and load and update`() {
        val hankeGeometriat = "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json"
            .asJsonResource(HankeGeometriat::class.java)
        hankeGeometriat.createdByUserId = "test"
        hankeGeometriat.modifiedByUserId = "test"

        // For FK constraints we need a Hanke in database
        // Using hankeService to create the dummy hanke into database causes
        // tunnus and id to be whatever the service thinks is right, so
        // they must be picked from the created hanke-instance.
        val hanke = hankeService.createHanke(Hanke(hankeGeometriat.hankeId!!, ""))
        val hankeTunnus = hanke.hankeTunnus!!
        hankeGeometriat.hankeId = hanke.id // replaces the id with the correct one
        // Check that the hanke geometry flag is false:
        assertThat(hanke.tilat.onGeometrioita).isFalse()

        // save
        hankeGeometriatService.saveGeometriat(hankeTunnus, hankeGeometriat)

        // NOTE: the local Hanke instance has not been updated by the above call. Need to reload
        // the hanke to check that the flag changed to true:
        val updatedHanke = hankeService.loadHanke(hankeTunnus)
        assertThat(updatedHanke).isNotNull()
        assertThat(updatedHanke!!.tilat.onGeometrioita).isTrue()

        // load
        var loadedHankeGeometriat = hankeGeometriatService.loadGeometriat(hankeTunnus)
        val createdAt = loadedHankeGeometriat!!.createdAt!!
        val modifiedAt = loadedHankeGeometriat.modifiedAt!!
        assertAll {
            assertThat(loadedHankeGeometriat!!.hankeId).isEqualTo(hankeGeometriat.hankeId)
            assertThat(loadedHankeGeometriat!!.version).isEqualTo(0)
            assertThat(loadedHankeGeometriat!!.createdByUserId).isEqualTo(hankeGeometriat.createdByUserId)
            assertThat(loadedHankeGeometriat!!.modifiedByUserId).isEqualTo(hankeGeometriat.modifiedByUserId)
            assertThat(loadedHankeGeometriat!!.featureCollection!!.features.size).isEqualTo(2)
            assertThat(loadedHankeGeometriat!!.featureCollection!!.features[0].geometry is Point)
            val loadedPoint = loadedHankeGeometriat!!.featureCollection!!.features[0].geometry as Point
            val point = hankeGeometriat.featureCollection!!.features[0].geometry as Point
            assertThat(loadedPoint.coordinates).isEqualTo(point.coordinates)
            assertThat(loadedHankeGeometriat!!.featureCollection!!.features[0].properties["hankeTunnus"])
                .isEqualTo(hankeTunnus)
        }

        // update
        loadedHankeGeometriat.featureCollection!!.features.add(loadedHankeGeometriat.featureCollection!!.features[0])
        loadedHankeGeometriat.id = null
        loadedHankeGeometriat.version = null
        loadedHankeGeometriat.modifiedAt = null

        // save
        hankeGeometriatService.saveGeometriat(hankeTunnus, loadedHankeGeometriat)

        // load
        loadedHankeGeometriat = hankeGeometriatService.loadGeometriat(hankeTunnus)
        assertAll {
            assertThat(loadedHankeGeometriat!!.hankeId).isEqualTo(hankeGeometriat.hankeId)
            assertThat(loadedHankeGeometriat.version).isEqualTo(1) // this has increased
            assertThat(loadedHankeGeometriat.createdByUserId).isEqualTo(hankeGeometriat.createdByUserId)
            assertThat(loadedHankeGeometriat.createdAt!!.format(DATABASE_TIMESTAMP_FORMAT)).isEqualTo(
                createdAt.format(
                    DATABASE_TIMESTAMP_FORMAT
                )
            )
            assertThat(loadedHankeGeometriat.modifiedAt!!.isAfter(modifiedAt)) // this has changed
            assertThat(loadedHankeGeometriat.modifiedByUserId).isEqualTo(hankeGeometriat.modifiedByUserId)
            assertThat(loadedHankeGeometriat.featureCollection!!.features.size).isEqualTo(3) // this has increased
            assertThat(loadedHankeGeometriat.featureCollection!!.features[0].geometry is Point)
            val loadedPoint = loadedHankeGeometriat.featureCollection!!.features[0].geometry as Point
            val point = hankeGeometriat.featureCollection!!.features[0].geometry as Point
            assertThat(loadedPoint.coordinates).isEqualTo(point.coordinates)
            assertThat(loadedHankeGeometriat.featureCollection!!.features[0].properties["hankeTunnus"]).isEqualTo(
                hankeTunnus
            )
        }

        // check database too to make sure there is everything correctly
        assertAll {
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM HankeGeometriat") { rs, _ -> rs.getInt(1) })
                .isEqualTo(1)
            val ids = jdbcTemplate.query("SELECT id, hankegeometriatid FROM HankeGeometria") { rs, _ ->
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
