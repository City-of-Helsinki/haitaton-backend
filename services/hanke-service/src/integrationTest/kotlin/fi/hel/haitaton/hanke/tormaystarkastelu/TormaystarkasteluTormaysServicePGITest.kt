package fi.hel.haitaton.hanke.tormaystarkastelu

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.geometria.HankeGeometriat
import fi.hel.haitaton.hanke.geometria.HankeGeometriatDao
import javax.transaction.Transactional
import org.junit.jupiter.api.BeforeEach
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

    @Autowired private lateinit var hankeRepository: HankeRepository
    @Autowired private lateinit var hankeGeometriatDao: HankeGeometriatDao

    @BeforeEach
    fun setUp() {
        val entity = hankeRepository.save(HankeEntity(hankeTunnus = "HAI21-1-testi"))
        hankeGeometriat =
            "/fi/hel/haitaton/hanke/tormaystarkastelu/hankeGeometriat.json".asJsonResource()
        hankeGeometriat.hankeId = entity.id
        hankeGeometriatDao.createHankeGeometriat(hankeGeometriat)
    }

    private var hankeGeometriat: HankeGeometriat = HankeGeometriat()

    @Autowired private lateinit var tormaysService: TormaystarkasteluTormaysService

    /*
    Test manually whether Hanke geometries are located on general street area ("yleinen katuosa", ylre_parts)
     */
    @Test
    fun yleisetKatuosat() {
        assertThat(tormaysService.anyIntersectsYleinenKatuosa(hankeGeometriat)).isTrue()
    }

    /*
    Test manually what general street classes (ylre_classes) Hanke geometries are located on
     */
    @Test
    fun yleisetKatuluokat() {
        assertThat(tormaysService.maxIntersectingYleinenkatualueKatuluokka(hankeGeometriat))
            .isEqualTo(TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU.value)
    }

    /*
    Test manually what street classes (street_classes) Hanke geometries are located on
     */
    @Test
    fun katuluokat() {
        assertThat(tormaysService.maxIntersectingLiikenteellinenKatuluokka(hankeGeometriat))
            .isEqualTo(TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU.value)
    }

    /*
    Test manually whether Hanke geometries are locaed on central businessa area ("kantakaupunki", central_business_area)
     */
    @Test
    fun kantakaupunki() {
        assertThat(tormaysService.anyIntersectsWithKantakaupunki(hankeGeometriat)).isTrue()
    }

    /*
    Test manually what kinds of traffic counts there are on Hanke geometries with radius of 15m
     */
    @Test
    fun liikennemaarat15() {
        assertThat(
                tormaysService.maxLiikennemaara(
                    hankeGeometriat,
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
        assertThat(
                tormaysService.maxLiikennemaara(
                    hankeGeometriat,
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
        assertThat(tormaysService.anyIntersectsCriticalBusRoutes(hankeGeometriat)).isFalse()
    }

    /*
    Test manually what bus routes each Hanke geometry is located on
     */
    @Test
    fun bussit() {
        val bussit = tormaysService.getIntersectingBusRoutes(hankeGeometriat)
        assertThat(bussit.size).isEqualTo(3)
        assertThat(bussit.map { it.reittiId }).containsOnly("1016", "1023N")
    }

    /*
    Test manually what tram lane types each Hanke geometry is located on
     */
    @Test
    fun raitiotiet() {
        assertThat(tormaysService.maxIntersectingTramByLaneType(hankeGeometriat))
            .isEqualTo(TormaystarkasteluRaitiotiekaistatyyppi.JAETTU.value)
    }

    /*
    Test manually what kind of cycleways Hanke geometries are located on
     */
    @Test
    fun pyorailyreitit() {
        assertThat(tormaysService.anyIntersectsWithCyclewaysPriority(hankeGeometriat)).isTrue()
        assertThat(tormaysService.anyIntersectsWithCyclewaysMain(hankeGeometriat)).isTrue()
    }
}
