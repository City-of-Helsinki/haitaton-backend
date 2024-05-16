package fi.hel.haitaton.hanke.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import fi.hel.haitaton.hanke.HANKEALUE_DEFAULT_NAME
import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.factory.DateFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withHankealue
import fi.hel.haitaton.hanke.factory.HankealueFactory
import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.tormaystarkastelu.Meluhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Polyhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Tarinahaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.VaikutusAutoliikenteenKaistamaariin
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class HankeTest {

    @ParameterizedTest(name = "There are {0} days between {1} and {2}")
    @CsvSource("1,2021-03-02,2021-03-02", "214,2021-03-01,2021-09-30")
    fun haittaAjanKesto(expectedNumberOfDays: Int, startDate: LocalDate, endDate: LocalDate) {
        val hanke = HankeFactory.create(id = 1, hankeTunnus = "HAI21-1")
        hanke.alueet.add(
            SavedHankealue(
                haittaAlkuPvm = startDate.atStartOfDay(TZ_UTC),
                haittaLoppuPvm = endDate.atStartOfDay(TZ_UTC),
                nimi = "$HANKEALUE_DEFAULT_NAME 1"
            )
        )
        val haittaAjanKesto = hanke.haittaAjanKestoDays
        assertThat(haittaAjanKesto!!).isEqualTo(expectedNumberOfDays)
    }

    @Test
    fun `Hanke alku and loppu calculated from alueet`() {
        val hanke = HankeFactory.create().withHankealue()
        val a = DateFactory.getEndDatetime()
        val b = DateFactory.getEndDatetime().plusMonths(1)
        val c = DateFactory.getEndDatetime().plusMonths(2)
        val d = DateFactory.getEndDatetime().plusMonths(3)
        hanke.alueet[0].haittaAlkuPvm = a
        hanke.alueet[0].haittaLoppuPvm = c
        val hankealue =
            HankealueFactory.create(
                haittaAlkuPvm = b,
                haittaLoppuPvm = d,
                kaistaHaitta =
                    VaikutusAutoliikenteenKaistamaariin
                        .VAHENTAA_SAMANAIKAISESTI_KAISTAN_KAHDELLA_AJOSUUNNALLA,
                kaistaPituusHaitta = AutoliikenteenKaistavaikutustenPituus.PITUUS_10_99_METRIA,
                meluHaitta = Meluhaitta.JATKUVA_MELUHAITTA,
                polyHaitta = Polyhaitta.JATKUVA_POLYHAITTA,
                tarinaHaitta = Tarinahaitta.JATKUVA_TARINAHAITTA,
            )
        hanke.alueet.add(hankealue)

        val alkuPvm = hanke.alkuPvm!!.format(DateTimeFormatter.BASIC_ISO_DATE)
        val aFormatted = a.format(DateTimeFormatter.BASIC_ISO_DATE)
        val loppuPvm = hanke.loppuPvm!!.format(DateTimeFormatter.BASIC_ISO_DATE)
        val dFormatted = d.format(DateTimeFormatter.BASIC_ISO_DATE)
        assertThat(alkuPvm).isEqualTo(aFormatted)
        assertThat(loppuPvm).isEqualTo(dFormatted)
    }
}
