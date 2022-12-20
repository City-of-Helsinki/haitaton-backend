package fi.hel.haitaton.hanke.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.factory.HankeFactory
import java.time.LocalDate
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

internal class HankeTest {

    @ParameterizedTest(name = "There are {0} days between {1} and {2}")
    @CsvSource("1,2021-03-02,2021-03-02", "214,2021-03-01,2021-09-30")
    fun haittaAjanKesto(expectedNumberOfDays: Int, startDate: LocalDate, endDate: LocalDate) {
        val hanke = HankeFactory.create(id = 1, hankeTunnus = "HAI21-1")
        hanke.alueet.add(
            Hankealue(
                haittaAlkuPvm = startDate.atStartOfDay(TZ_UTC),
                haittaLoppuPvm = endDate.atStartOfDay(TZ_UTC)
            )
        )
        val haittaAjanKesto = hanke.haittaAjanKestoDays
        assertThat(haittaAjanKesto!!).isEqualTo(expectedNumberOfDays)
    }
}
