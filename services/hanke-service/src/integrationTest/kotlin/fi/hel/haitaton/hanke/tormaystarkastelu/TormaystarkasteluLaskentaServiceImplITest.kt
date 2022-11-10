package fi.hel.haitaton.hanke.tormaystarkastelu

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.ninjasquad.springmockk.MockkBean
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.KaistajarjestelynPituus
import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.geometria.HankeGeometriat
import fi.hel.haitaton.hanke.geometria.HankeGeometriatDao
import io.mockk.every
import java.time.ZonedDateTime
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
@Transactional
@WithMockUser(username = "test", roles = ["haitaton-user"])
internal class TormaystarkasteluLaskentaServiceImplITest : DatabaseTest() {

    @MockkBean private lateinit var hankeGeometriatDao: HankeGeometriatDao
    @MockkBean private lateinit var tormaystarkasteluDao: TormaystarkasteluTormaysService

    @Autowired private lateinit var hankeService: HankeService

    @Autowired
    private lateinit var tormaystarkasteluLaskentaService: TormaystarkasteluLaskentaService

    @Test
    fun `calculateTormaystarkastelu happy case`() {
        val hanke = setupHappyCase()

        // Call calculate (which should save stuff...), ignore the returned hanke
        val tulos = tormaystarkasteluLaskentaService.calculateTormaystarkastelu(hanke)
        assertThat(tulos).isNotNull()
        assertThat(tulos!!.liikennehaittaIndeksi).isNotNull()
        assertThat(tulos.liikennehaittaIndeksi.indeksi).isNotNull()
        assertThat(tulos.liikennehaittaIndeksi.indeksi).isEqualTo(4.0f)
    }

    private fun setupHappyCase(): Hanke {
        val tmp = Hanke()
        tmp.apply {
            alkuPvm = ZonedDateTime.of(2021, 3, 4, 0, 0, 0, 0, TZ_UTC)
            loppuPvm = alkuPvm!!.plusDays(7)
            haittaAlkuPvm = alkuPvm
            haittaLoppuPvm = loppuPvm
            kaistaHaitta = TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.YKSI
            kaistaPituusHaitta = KaistajarjestelynPituus.YKSI
        }
        val hanke = hankeService.createHanke(tmp)

        val hankeId = hanke.id!!
        val hankeGeometriatId = 1
        val hankeGeometriat = HankeGeometriat(hankeGeometriatId, hankeId)
        hankeService.updateHanke(hanke)
        hanke.geometriat = hankeGeometriat

        every { hankeGeometriatDao.retrieveHankeGeometriat(hankeId) } returns hankeGeometriat
        every { tormaystarkasteluDao.anyIntersectsYleinenKatuosa(hankeGeometriat) } returns true
        every {
            tormaystarkasteluDao.maxIntersectingYleinenkatualueKatuluokka(hankeGeometriat)
        } returns TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU.value
        every {
            tormaystarkasteluDao.maxIntersectingLiikenteellinenKatuluokka(hankeGeometriat)
        } returns TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU.value
        every {
            tormaystarkasteluDao.maxLiikennemaara(
                hankeGeometriat,
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_30
            )
        } returns 1000
        every { tormaystarkasteluDao.anyIntersectsWithCyclewaysPriority(hankeGeometriat) } returns
            false
        every { tormaystarkasteluDao.anyIntersectsWithCyclewaysMain(hankeGeometriat) } returns true
        every { tormaystarkasteluDao.maxIntersectingTramByLaneType(hankeGeometriat) } returns
            TormaystarkasteluRaitiotiekaistatyyppi.JAETTU.value
        every { tormaystarkasteluDao.anyIntersectsCriticalBusRoutes(hankeGeometriat) } returns true

        return hanke
    }
}
