package fi.hel.haitaton.hanke.tormaystarkastelu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvFileSource
import org.mockito.Mockito

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class TormaystarkasteluLaskentaServiceTest {

    private val tormaysService: TormaystarkasteluTormaysService =
            Mockito.mock(TormaystarkasteluTormaysService::class.java)
    private val service: TormaystarkasteluLaskentaService = TormaystarkasteluLaskentaService(
            LuokitteluRajaArvotServiceHardCoded(),
            PerusIndeksiPainotServiceHardCoded(),
            tormaysService)

    @ParameterizedTest(name = "Perusindeksi with default weights should be {0}")
    @CsvFileSource(resources = [ "perusindeksi-test.csv" ], numLinesToSkip = 1)
    fun perusIndeksiCalculatorTest(
            indeksi: Float,
            haittaAjanKesto: Int,
            todennakoinenHaittaPaaAjoratojenKaistajarjestelyihin: Int,
            kaistajarjestelynPituus: Int,
            katuluokka: Int,
            liikennemaara: Int) {
        val luokittelu = mapOf(
                LuokitteluType.HAITTA_AJAN_KESTO to haittaAjanKesto,
                LuokitteluType.TODENNAKOINEN_HAITTA_PAAAJORATOJEN_KAISTAJARJESTELYIHIN to
                        todennakoinenHaittaPaaAjoratojenKaistajarjestelyihin,
                LuokitteluType.KAISTAJARJESTELYN_PITUUS to kaistajarjestelynPituus,
                LuokitteluType.KATULUOKKA to katuluokka,
                LuokitteluType.LIIKENNEMAARA to liikennemaara
        )
        val perusindeksi = service.calculatePerusIndeksiFromLuokittelu(luokittelu)
        assertThat(perusindeksi).isEqualTo(indeksi)
    }

}
