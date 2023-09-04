package fi.hel.haitaton.hanke.tormaystarkastelu

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.geometria.Geometriat
import fi.hel.haitaton.hanke.geometria.GeometriatDao
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
internal class TormaystarkasteluTormaysServicePGITest : DatabaseTest() {

    @Autowired private lateinit var geometriatDao: GeometriatDao

    @Autowired private lateinit var tormaysService: TormaystarkasteluTormaysService

    private fun createHankeGeometria(): Geometriat {
        val geometriat =
            "/fi/hel/haitaton/hanke/tormaystarkastelu/hankeGeometriat.json".asJsonResource(
                Geometriat::class.java
            )
        return geometriatDao.createGeometriat(geometriat)
    }

    /**
     * Test manually whether Hanke geometries are located on general street area ("yleinen katuosa",
     * ylre_parts)
     */
    @Test
    fun yleisetKatuosat() {
        val geometriat = createHankeGeometria()
        assertThat(tormaysService.anyIntersectsYleinenKatuosa(arrayListOf(geometriat))).isTrue()
    }

    /** Test manually what general street classes (ylre_classes) Hanke geometries are located on */
    @Test
    fun yleisetKatuluokat() {
        val geometriat = createHankeGeometria()
        assertThat(tormaysService.maxIntersectingYleinenkatualueKatuluokka(arrayListOf(geometriat)))
            .isEqualTo(TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU.value)
    }

    /** Test manually what street classes (street_classes) Hanke geometries are located on */
    @Test
    fun katuluokat() {
        val geometriat = createHankeGeometria()
        assertThat(tormaysService.maxIntersectingLiikenteellinenKatuluokka(arrayListOf(geometriat)))
            .isEqualTo(TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU.value)
    }

    /**
     * Test manually whether Hanke geometries are locaed on central businessa area ("kantakaupunki",
     * central_business_area)
     */
    @Test
    fun kantakaupunki() {
        val geometriat = createHankeGeometria()
        assertThat(tormaysService.anyIntersectsWithKantakaupunki(arrayListOf(geometriat))).isTrue()
    }

    /**
     * Test manually what kinds of traffic counts there are on Hanke geometries with radius of 15m
     */
    @Test
    fun liikennemaarat15() {
        val geometriat = createHankeGeometria()
        assertThat(
                tormaysService.maxLiikennemaara(
                    arrayListOf(geometriat),
                    TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_15
                )
            )
            .isEqualTo(17566)
    }

    /**
     * Test manually what kinds of traffic counts there are on Hanke geometries with radius of 30m
     */
    @Test
    fun liikennemaarat30() {
        val geometriat = createHankeGeometria()
        assertThat(
                tormaysService.maxLiikennemaara(
                    arrayListOf(geometriat),
                    TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_30
                )
            )
            .isEqualTo(17566)
    }

    /** Test manually what Hanke geometries are located on critical area for buses */
    @Test
    fun bussiliikenteenKannaltaKriittinenAlue() {
        val geometriat = createHankeGeometria()
        assertThat(tormaysService.anyIntersectsCriticalBusRoutes(arrayListOf(geometriat))).isFalse()
    }

    /** Test manually what bus routes each Hanke geometry is located on */
    @Test
    fun bussit() {
        val geometriat = createHankeGeometria()
        val bussit = tormaysService.getIntersectingBusRoutes(arrayListOf(geometriat))
        assertThat(bussit.size).isEqualTo(3)
        assertThat(bussit.map { it.reittiId }).containsOnly("1016", "1023N")
    }

    /** Test manually what tram lane types each Hanke geometry is located on */
    @Test
    fun raitiotiet() {
        val geometriat = createHankeGeometria()
        assertThat(tormaysService.maxIntersectingTramByLaneType(arrayListOf(geometriat)))
            .isEqualTo(TormaystarkasteluRaitiotiekaistatyyppi.JAETTU.value)
    }

    /** Test manually what kind of cycleways Hanke geometries are located on */
    @Test
    fun pyorailyreitit() {
        val geometriat = createHankeGeometria()
        assertThat(tormaysService.anyIntersectsWithCyclewaysPriority(arrayListOf(geometriat)))
            .isTrue()
        assertThat(tormaysService.anyIntersectsWithCyclewaysMain(arrayListOf(geometriat))).isTrue()
    }
}
