package fi.hel.haitaton.hanke.tormaystarkastelu

import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import fi.hel.haitaton.hanke.HankeStatus
import fi.hel.haitaton.hanke.KaistajarjestelynPituus
import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.Hankealue
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.geometria.Geometriat
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvFileSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class TormaystarkasteluLaskentaServiceTest {

    private val tormaysService: TormaystarkasteluTormaysService = mockk()
    private val laskentaService = TormaystarkasteluLaskentaService(tormaysService)

    @ParameterizedTest(name = "Perusindeksi with default weights should be {0}")
    @CsvFileSource(resources = ["perusindeksi-test.csv"], numLinesToSkip = 1)
    fun perusIndeksiCalculatorTest(
        indeksi: Float,
        haittaAjanKesto: Int,
        todennakoinenHaittaPaaAjoratojenKaistajarjestelyihin: Int,
        kaistajarjestelynPituus: Int,
        katuluokka: Int,
        liikennemaara: Int
    ) {
        val luokittelu =
            mapOf(
                LuokitteluType.HAITTA_AJAN_KESTO to haittaAjanKesto,
                LuokitteluType.TODENNAKOINEN_HAITTA_PAAAJORATOJEN_KAISTAJARJESTELYIHIN to
                    todennakoinenHaittaPaaAjoratojenKaistajarjestelyihin,
                LuokitteluType.KAISTAJARJESTELYN_PITUUS to kaistajarjestelynPituus,
                LuokitteluType.KATULUOKKA to katuluokka,
                LuokitteluType.LIIKENNEMAARA to liikennemaara
            )
        val perusindeksi = laskentaService.calculatePerusIndeksiFromLuokittelu(luokittelu)
        assertThat(perusindeksi).isEqualTo(indeksi)
    }

    @Test
    fun `calculateTormaystarkastelu happy case`() {
        val hanke = setupHappyCase()

        val tulos = laskentaService.calculateTormaystarkastelu(hanke)

        assertk.assertThat(tulos).isNotNull()
        assertk.assertThat(tulos!!.liikennehaittaIndeksi).isNotNull()
        assertk.assertThat(tulos.liikennehaittaIndeksi.indeksi).isNotNull()
        assertk.assertThat(tulos.liikennehaittaIndeksi.indeksi).isEqualTo(4.0f)
    }

    private fun setupHappyCase(): Hanke {
        val geometriat =
            "/fi/hel/haitaton/hanke/geometria/hankeGeometriat.json".asJsonResource(
                Geometriat::class.java
            )

        val alkuPvm = ZonedDateTime.of(2021, 3, 4, 0, 0, 0, 0, TZ_UTC)
        val hanke =
            HankeFactory.create(
                nimi = "hanke",
                vaihe = Vaihe.OHJELMOINTI,
                hankeStatus = HankeStatus.DRAFT
            )
        hanke.alueet.add(
            Hankealue(
                geometriat = geometriat,
                haittaAlkuPvm = alkuPvm,
                haittaLoppuPvm = alkuPvm.plusDays(7),
                kaistaHaitta = TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.YKSI,
                kaistaPituusHaitta = KaistajarjestelynPituus.YKSI
            )
        )

        every { tormaysService.anyIntersectsYleinenKatuosa(hanke.alueidenGeometriat()) } returns
            true
        every {
            tormaysService.maxIntersectingYleinenkatualueKatuluokka(hanke.alueidenGeometriat())
        } returns TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU.value
        every {
            tormaysService.maxIntersectingLiikenteellinenKatuluokka(hanke.alueidenGeometriat())
        } returns TormaystarkasteluKatuluokka.ALUEELLINEN_KOKOOJAKATU.value
        every {
            tormaysService.maxLiikennemaara(
                hanke.alueidenGeometriat(),
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_30
            )
        } returns 1000
        every {
            tormaysService.anyIntersectsWithCyclewaysPriority(hanke.alueidenGeometriat())
        } returns false
        every { tormaysService.anyIntersectsWithCyclewaysMain(hanke.alueidenGeometriat()) } returns
            true
        every { tormaysService.maxIntersectingTramByLaneType(hanke.alueidenGeometriat()) } returns
            TormaystarkasteluRaitiotiekaistatyyppi.JAETTU.value
        every { tormaysService.anyIntersectsCriticalBusRoutes(hanke.alueidenGeometriat()) } returns
            true

        return hanke
    }
}
