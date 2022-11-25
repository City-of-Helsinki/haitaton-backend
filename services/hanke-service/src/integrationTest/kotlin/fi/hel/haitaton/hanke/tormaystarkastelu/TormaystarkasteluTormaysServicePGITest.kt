package fi.hel.haitaton.hanke.tormaystarkastelu

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import fi.hel.haitaton.hanke.*
import fi.hel.haitaton.hanke.geometria.HankeGeometriat
import fi.hel.haitaton.hanke.geometria.HankeGeometriatDao
import javax.transaction.Transactional
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
@Transactional
internal class TormaystarkasteluTormaysServicePGITest : DatabaseTest() {

    @Autowired private lateinit var hankeGeometriatDao: HankeGeometriatDao

    @Autowired private lateinit var tormaysService: TormaystarkasteluTormaysService

    private fun createHankeGeometria(): HankeGeometriat {
        val hankeGeometriat =
            "/fi/hel/haitaton/hanke/tormaystarkastelu/hankeGeometriat.json".asJsonResource(
                HankeGeometriat::class.java
            )
        val luotu = hankeGeometriatDao.createHankeGeometriat(hankeGeometriat)
        return luotu!!
    }

    /*
    Test manually whether Hanke geometries are located on general street area ("yleinen katuosa", ylre_parts)
     */
    @Test
    fun yleisetKatuosat() {
        val hankeGeometriat = createHankeGeometria()
        assertThat(tormaysService.anyIntersectsYleinenKatuosa(arrayListOf(hankeGeometriat)))
            .isTrue()
    }

    /*
    Test manually what general street classes (ylre_classes) Hanke geometries are located on
     */
    @Test
    fun yleisetKatuluokat() {
        val hankeGeometriat = createHankeGeometria()
        assertThat(
                tormaysService.maxIntersectingYleinenkatualueKatuluokka(
                    arrayListOf(hankeGeometriat)
                )
            )
            .isEqualTo(TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU.value)
    }

    /*
    Test manually what street classes (street_classes) Hanke geometries are located on
     */
    @Test
    fun katuluokat() {
        val hankeGeometriat = createHankeGeometria()
        assertThat(
                tormaysService.maxIntersectingLiikenteellinenKatuluokka(
                    arrayListOf(hankeGeometriat)
                )
            )
            .isEqualTo(TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU.value)
    }

    /*
    Test manually whether Hanke geometries are locaed on central businessa area ("kantakaupunki", central_business_area)
     */
    @Test
    fun kantakaupunki() {
        val hankeGeometriat = createHankeGeometria()
        assertThat(tormaysService.anyIntersectsWithKantakaupunki(arrayListOf(hankeGeometriat)))
            .isTrue()
    }

    /*
    Test manually what kinds of traffic counts there are on Hanke geometries with radius of 15m
     */
    @Test
    fun liikennemaarat15() {
        val hankeGeometriat = createHankeGeometria()
        assertThat(
                tormaysService.maxLiikennemaara(
                    arrayListOf(hankeGeometriat),
                    TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_15
                )
            )
            .isEqualTo(17566)
    }

    /*
    Test manually what kinds of traffic counts there are on Hanke geometries with radius of 30m
     */
    @Test
    fun liikennemaarat30() {
        val hankeGeometriat = createHankeGeometria()
        assertThat(
                tormaysService.maxLiikennemaara(
                    arrayListOf(hankeGeometriat),
                    TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_30
                )
            )
            .isEqualTo(17566)
    }

    /*
    Test manually what Hanke geometries are located on critical area for buses
     */
    @Test
    fun bussiliikenteenKannaltaKriittinenAlue() {
        val hankeGeometriat = createHankeGeometria()
        assertThat(tormaysService.anyIntersectsCriticalBusRoutes(arrayListOf(hankeGeometriat)))
            .isFalse()
    }

    /*
    Test manually what bus routes each Hanke geometry is located on
     */
    @Test
    fun bussit() {
        val hankeGeometriat = createHankeGeometria()
        val bussit = tormaysService.getIntersectingBusRoutes(arrayListOf(hankeGeometriat))
        assertThat(bussit.size).isEqualTo(3)
        assertThat(bussit.map { it.reittiId }).containsOnly("1016", "1023N")
    }

    /*
    Test manually what tram lane types each Hanke geometry is located on
     */
    @Test
    fun raitiotiet() {
        val hankeGeometriat = createHankeGeometria()
        assertThat(tormaysService.maxIntersectingTramByLaneType(arrayListOf(hankeGeometriat)))
            .isEqualTo(TormaystarkasteluRaitiotiekaistatyyppi.JAETTU.value)
    }

    /*
    Test manually what kind of cycleways Hanke geometries are located on
     */
    @Test
    fun pyorailyreitit() {
        val hankeGeometriat = createHankeGeometria()
        assertThat(tormaysService.anyIntersectsWithCyclewaysPriority(arrayListOf(hankeGeometriat)))
            .isTrue()
        assertThat(tormaysService.anyIntersectsWithCyclewaysMain(arrayListOf(hankeGeometriat)))
            .isTrue()
    }
}
