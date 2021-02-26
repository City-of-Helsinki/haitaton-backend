package fi.hel.haitaton.hanke.tormaystarkastelu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class TormaystarkasteluCalculatorTest {

    @Test
    fun calculateAllIndeksit_whenIsNotPyorailyreitti() {
        //TODO: other classifications to be passed to not cause any errors?
        val luokittelut = mutableListOf(
            Luokittelutulos(
                1,
                LuokitteluType.PYORAILYN_PAAREITTI,
                0,
                PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()
            )
        )
        val result = TormaystarkasteluCalculator().calculateAllIndeksit(TormaystarkasteluTulos("TUNNUS"), luokittelut)
        assertThat(result.pyorailyIndeksi).isEqualTo(1.0f)
    }
}
