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

    @Test
    fun calculateAllIndeksit_whenIsPyorailynPaareittiOrPriority() {
        //TODO: other classifications to be passed to not cause any errors?

        // 4th case:
        var luokittelut = mutableListOf(
            Luokittelutulos(
                1,
                LuokitteluType.PYORAILYN_PAAREITTI,
                4,
                PyorailyTormaysLuokittelu.PAAREITTI.toString()
            )
        )
        var result = TormaystarkasteluCalculator().calculateAllIndeksit(TormaystarkasteluTulos("TUNNUS"), luokittelut)
        assertThat(result.pyorailyIndeksi).isEqualTo(3.0f)

        // top case:
       luokittelut = mutableListOf(
            Luokittelutulos(
                1,
                LuokitteluType.PYORAILYN_PAAREITTI,
                5,
                PyorailyTormaysLuokittelu.PRIORISOITU_REITTI.toString()
            )
        )
        result = TormaystarkasteluCalculator().calculateAllIndeksit(TormaystarkasteluTulos("TUNNUS"), luokittelut)
        assertThat(result.pyorailyIndeksi).isEqualTo(3.0f)
    }

    @Test
    fun calculateAllIndeksit_whenIsNotOnRaitiovaunuOrBusLine() {
        //TODO: other classifications to be passed to not cause any errors?
        val luokittelut = mutableListOf(
            Luokittelutulos(
                1,
                LuokitteluType.PYORAILYN_PAAREITTI,
                0,
                PyorailyTormaysLuokittelu.EI_PYORAILUREITTI.toString()
            ),
            Luokittelutulos(
                1,
                LuokitteluType.RAITIOVAUNULIIKENNE,
                0,
                RaitiovaunuTormaysLuokittelu.EI_RAITIOVAUNULIIKENNETTA.toString()
            ),
            Luokittelutulos(1, LuokitteluType.BUSSILIIKENNE,0, "",   )
        )
        val result = TormaystarkasteluCalculator().calculateAllIndeksit(TormaystarkasteluTulos("TUNNUS"), luokittelut)
        assertThat(result.pyorailyIndeksi).isEqualTo(1.0f)
        assertThat(result.joukkoliikenneIndeksi).isEqualTo(1.0f)
    }

}
