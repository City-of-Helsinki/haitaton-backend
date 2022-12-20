package fi.hel.haitaton.hanke.tormaystarkastelu

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import com.ninjasquad.springmockk.MockkBean
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.KaistajarjestelynPituus
import fi.hel.haitaton.hanke.SaveType
import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.Hankealue
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.geometria.Geometriat
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
        val geometriat =
            "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json".asJsonResource(
                Geometriat::class.java
            )

        val alkuPvm = ZonedDateTime.of(2021, 3, 4, 0, 0, 0, 0, TZ_UTC)
        val loppuPvm = alkuPvm!!.plusDays(7)
        val tmp =
            HankeFactory.create(
                id = null,
                hankeTunnus = null,
                nimi = "hanke",
                alkuPvm = alkuPvm,
                loppuPvm = loppuPvm,
                vaihe = Vaihe.OHJELMOINTI,
                saveType = SaveType.DRAFT
            )
        tmp.alueet.add(
            Hankealue(
                geometriat = geometriat,
                haittaAlkuPvm = alkuPvm,
                haittaLoppuPvm = alkuPvm.plusDays(7),
                kaistaHaitta = TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.YKSI,
                kaistaPituusHaitta = KaistajarjestelynPituus.YKSI
            )
        )

        val hanke = hankeService.createHanke(tmp)

        every {
            tormaystarkasteluDao.anyIntersectsYleinenKatuosa(hanke.alueidenGeometriat())
        } returns true
        every {
            tormaystarkasteluDao.maxIntersectingYleinenkatualueKatuluokka(
                hanke.alueidenGeometriat()
            )
        } returns TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU.value
        every {
            tormaystarkasteluDao.maxIntersectingLiikenteellinenKatuluokka(
                hanke.alueidenGeometriat()
            )
        } returns TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU.value
        every {
            tormaystarkasteluDao.maxLiikennemaara(
                hanke.alueidenGeometriat(),
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_30
            )
        } returns 1000
        every {
            tormaystarkasteluDao.anyIntersectsWithCyclewaysPriority(hanke.alueidenGeometriat())
        } returns false
        every {
            tormaystarkasteluDao.anyIntersectsWithCyclewaysMain(hanke.alueidenGeometriat())
        } returns true
        every {
            tormaystarkasteluDao.maxIntersectingTramByLaneType(hanke.alueidenGeometriat())
        } returns TormaystarkasteluRaitiotiekaistatyyppi.JAETTU.value
        every {
            tormaystarkasteluDao.anyIntersectsCriticalBusRoutes(hanke.alueidenGeometriat())
        } returns true

        return hanke
    }
}
