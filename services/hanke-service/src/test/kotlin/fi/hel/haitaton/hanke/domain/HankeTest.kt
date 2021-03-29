package fi.hel.haitaton.hanke.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import fi.hel.haitaton.hanke.TZ_UTC
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.LocalDate

internal class HankeTest {

    @ParameterizedTest(name = "There are {0} days between {1} and {2}")
    @CsvSource("1,2021-03-02,2021-03-02", "214,2021-03-01,2021-09-30")
    fun haittaAjanKesto(expectedNumberOfDays: Long, startDate: LocalDate, endDate: LocalDate) {
        val hanke = Hanke(1, "HAI21-1")
        hanke.haittaAlkuPvm = startDate.atStartOfDay(TZ_UTC)
        hanke.haittaLoppuPvm = endDate.atStartOfDay(TZ_UTC)
        val haittaAjanKesto = hanke.haittaAjanKestoDays
        assertThat(haittaAjanKesto!!).isEqualTo(expectedNumberOfDays)
    }
}
