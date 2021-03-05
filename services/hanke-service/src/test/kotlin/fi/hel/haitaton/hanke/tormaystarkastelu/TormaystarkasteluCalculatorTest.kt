package fi.hel.haitaton.hanke.tormaystarkastelu

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.aggregator.AggregateWith
import org.junit.jupiter.params.aggregator.ArgumentsAccessor
import org.junit.jupiter.params.aggregator.ArgumentsAggregator
import org.junit.jupiter.params.provider.CsvFileSource
import java.util.*

internal class TormaystarkasteluCalculatorTest {

    // initial classifications are empty - overwrite those needed in each test
    private val classifications =
        LuokitteluType.values().associateWith { Luokittelutulos(it, 0, "") }.toMutableMap()

    @ParameterizedTest(name = "{index} {1} is expecting to have 'perusindeksi' of {0}")
    @CsvFileSource(resources = ["/fi/hel/haitaton/hanke/tormaystarkastelu/perusindeksi-test.csv"], numLinesToSkip = 1)
    fun perusindeksi(
        expectedIndex: Float,
        @AggregateWith(LuokitteluMapAggregator::class) classifications: Map<LuokitteluType, Luokittelutulos>
    ) {
        // overwrite initial classifications with values from CSV test data
        classifications.forEach { (key, value) ->
            this.classifications[key] = value
        }

        val result = TormaystarkasteluCalculator
            .calculateAllIndeksit(TormaystarkasteluTulos("TUNNUS"), this.classifications)

        assertThat(result.perusIndeksi).isEqualTo(expectedIndex)
    }

    @Test
    fun calculateAllIndeksit_whenIsNotPyorailyreitti() {
        classifications[LuokitteluType.PYORAILYN_PAAREITTI] =
            Luokittelutulos(LuokitteluType.PYORAILYN_PAAREITTI, 0, "")

        val result = TormaystarkasteluCalculator
            .calculateAllIndeksit(TormaystarkasteluTulos("TUNNUS"), classifications)

        assertThat(result.pyorailyIndeksi).isEqualTo(1.0f)
    }

    @Test
    fun calculateAllIndeksit_whenIsPyorailynPaareittiOrPriority() {
        // 4th case:
        classifications[LuokitteluType.PYORAILYN_PAAREITTI] =
            Luokittelutulos(LuokitteluType.PYORAILYN_PAAREITTI, 4, "")

        var result = TormaystarkasteluCalculator.calculateAllIndeksit(TormaystarkasteluTulos("TUNNUS"), classifications)

        assertThat(result.pyorailyIndeksi).isEqualTo(3.0f)

        // top case:
        classifications[LuokitteluType.PYORAILYN_PAAREITTI] =
            Luokittelutulos(LuokitteluType.PYORAILYN_PAAREITTI, 5, "")

        result = TormaystarkasteluCalculator.calculateAllIndeksit(TormaystarkasteluTulos("TUNNUS"), classifications)

        assertThat(result.pyorailyIndeksi).isEqualTo(3.0f)
    }

    @Test
    fun calculateAllIndeksit_whenIsNotOnRaitiovaunuOrBusLine() {
        classifications[LuokitteluType.RAITIOVAUNULIIKENNE] =
            Luokittelutulos(LuokitteluType.RAITIOVAUNULIIKENNE, 0, "")
        classifications[LuokitteluType.BUSSILIIKENNE] =
            Luokittelutulos(LuokitteluType.BUSSILIIKENNE, 0, "")

        val result = TormaystarkasteluCalculator.calculateAllIndeksit(TormaystarkasteluTulos("TUNNUS"), classifications)

        assertThat(result.pyorailyIndeksi).isEqualTo(1.0f)
        assertThat(result.joukkoliikenneIndeksi).isEqualTo(1.0f)
    }

    @Test
    fun calculateAllIndeksit_whenIsNotOnRaitiovaunuButIsOnBusLine() {
        classifications[LuokitteluType.PYORAILYN_PAAREITTI] =
            Luokittelutulos(LuokitteluType.PYORAILYN_PAAREITTI, 0, "")
        classifications[LuokitteluType.RAITIOVAUNULIIKENNE] =
            Luokittelutulos(LuokitteluType.RAITIOVAUNULIIKENNE, 0, "")
        classifications[LuokitteluType.BUSSILIIKENNE] =
            Luokittelutulos(LuokitteluType.BUSSILIIKENNE, 4, "")

        val result = TormaystarkasteluCalculator.calculateAllIndeksit(TormaystarkasteluTulos("TUNNUS"), classifications)

        assertThat(result.pyorailyIndeksi).isEqualTo(1.0f)
        assertThat(result.joukkoliikenneIndeksi).isEqualTo(4.0f)
    }

    @Test
    fun calculateAllIndeksit_whenIsOnRaitiovaunuAndIsLowerOnBusLine() {
        classifications[LuokitteluType.PYORAILYN_PAAREITTI] =
            Luokittelutulos(LuokitteluType.PYORAILYN_PAAREITTI, 0, "")
        classifications[LuokitteluType.RAITIOVAUNULIIKENNE] =
            Luokittelutulos(LuokitteluType.RAITIOVAUNULIIKENNE, 4, "")
        classifications[LuokitteluType.BUSSILIIKENNE] =
            Luokittelutulos(LuokitteluType.BUSSILIIKENNE, 2, "")

        val result = TormaystarkasteluCalculator.calculateAllIndeksit(TormaystarkasteluTulos("TUNNUS"), classifications)

        assertThat(result.pyorailyIndeksi).isEqualTo(1.0f)
        assertThat(result.joukkoliikenneIndeksi).isEqualTo(4.0f)
    }

    @Test
    fun calculateAllIndeksit_whenBothRaitiovaunuAndBusLinesAreOnTopClass() {
        classifications[LuokitteluType.PYORAILYN_PAAREITTI] =
            Luokittelutulos(LuokitteluType.PYORAILYN_PAAREITTI, 0, "")
        classifications[LuokitteluType.RAITIOVAUNULIIKENNE] =
            Luokittelutulos(LuokitteluType.RAITIOVAUNULIIKENNE, 5, "")
        classifications[LuokitteluType.BUSSILIIKENNE] =
            Luokittelutulos(LuokitteluType.BUSSILIIKENNE, 5, "")

        val result = TormaystarkasteluCalculator.calculateAllIndeksit(TormaystarkasteluTulos("TUNNUS"), classifications)

        assertThat(result.pyorailyIndeksi).isEqualTo(1.0f)
        assertThat(result.joukkoliikenneIndeksi).isEqualTo(4.0f)
    }


    @Test
    fun calculateAllIndeksit_checkThatLiikenneHaittaIndeksiIsFromJoukkoliikenne() {

        classifications[LuokitteluType.PYORAILYN_PAAREITTI] =
            Luokittelutulos(1, LuokitteluType.PYORAILYN_PAAREITTI, 0, "")
        classifications[LuokitteluType.RAITIOVAUNULIIKENNE] =
            Luokittelutulos(1, LuokitteluType.RAITIOVAUNULIIKENNE, 2, "")
        classifications[LuokitteluType.BUSSILIIKENNE] =
            Luokittelutulos(1, LuokitteluType.BUSSILIIKENNE, 4, "")

        val result = TormaystarkasteluCalculator.calculateAllIndeksit(TormaystarkasteluTulos("TUNNUS"), classifications)
        assertThat(result.pyorailyIndeksi).isEqualTo(1.0f)
        assertThat(result.joukkoliikenneIndeksi).isEqualTo(4.0f)

        assertThat(result.liikennehaittaIndeksi?.indeksi).isEqualTo(4.0f)
        assertThat(result.liikennehaittaIndeksi?.type).isEqualTo(IndeksiType.JOUKKOLIIKENNEINDEKSI)
    }

    @Test
    fun calculateAllIndeksit_checkThatLiikenneHaittaIndeksiIsFromPyorailuIndeksi() {

        classifications[LuokitteluType.PYORAILYN_PAAREITTI] =
            Luokittelutulos(1, LuokitteluType.PYORAILYN_PAAREITTI, 4, "")
        classifications[LuokitteluType.RAITIOVAUNULIIKENNE] =
            Luokittelutulos(1, LuokitteluType.RAITIOVAUNULIIKENNE, 0, "")
        classifications[LuokitteluType.BUSSILIIKENNE] =
            Luokittelutulos(1, LuokitteluType.BUSSILIIKENNE, 2, "")

        val result = TormaystarkasteluCalculator.calculateAllIndeksit(TormaystarkasteluTulos("TUNNUS"), classifications)

        assertThat(result.pyorailyIndeksi).isEqualTo(3.0f)
        assertThat(result.joukkoliikenneIndeksi).isEqualTo(1.0f)

        assertThat(result.liikennehaittaIndeksi?.indeksi).isEqualTo(3.0f)
        assertThat(result.liikennehaittaIndeksi?.type).isEqualTo(IndeksiType.PYORAILYINDEKSI)
    }

    @Test
    fun calculateAllIndeksit_checkThatLiikenneHaittaIndeksiIsFromPerusIndeksi() {

        // perusindeksi will be: 5*0,2 + 5*0,25 + 5*0,1 + 5*0,25
        classifications[LuokitteluType.KATULUOKKA] =
            Luokittelutulos(1, LuokitteluType.KATULUOKKA, 5, "")
        classifications[LuokitteluType.LIIKENNEMAARA] =
            Luokittelutulos(1, LuokitteluType.LIIKENNEMAARA, 5, "")
        classifications[LuokitteluType.HAITTA_AJAN_KESTO] =
            Luokittelutulos(1, LuokitteluType.HAITTA_AJAN_KESTO, 5, "")
        classifications[LuokitteluType.TODENNAKOINEN_HAITTA_PAAAJORATOJEN_KAISTAJARJESTELYIHIN] =
            Luokittelutulos(1, LuokitteluType.TODENNAKOINEN_HAITTA_PAAAJORATOJEN_KAISTAJARJESTELYIHIN,
                5, "")

        classifications[LuokitteluType.PYORAILYN_PAAREITTI] =
            Luokittelutulos(1, LuokitteluType.PYORAILYN_PAAREITTI, 2, "")
        classifications[LuokitteluType.RAITIOVAUNULIIKENNE] =
            Luokittelutulos(1, LuokitteluType.RAITIOVAUNULIIKENNE, 0, "")
        classifications[LuokitteluType.BUSSILIIKENNE] =
            Luokittelutulos(1, LuokitteluType.BUSSILIIKENNE, 2, "")

        val result = TormaystarkasteluCalculator.calculateAllIndeksit(TormaystarkasteluTulos("TUNNUS"), classifications)

        assertThat(result.perusIndeksi).isEqualTo(4.0f)
        assertThat(result.pyorailyIndeksi).isEqualTo(1.0f)
        assertThat(result.joukkoliikenneIndeksi).isEqualTo(1.0f)

        assertThat(result.liikennehaittaIndeksi?.indeksi).isEqualTo(4.0f)
        assertThat(result.liikennehaittaIndeksi?.type).isEqualTo(IndeksiType.PERUSINDEKSI)
    }
}

class LuokitteluMapAggregator : ArgumentsAggregator {
    override fun aggregateArguments(accessor: ArgumentsAccessor, context: ParameterContext): Any {
        val map = mutableMapOf<LuokitteluType, Luokittelutulos>()
        EnumSet.of(
            LuokitteluType.HAITTA_AJAN_KESTO,
            LuokitteluType.TODENNAKOINEN_HAITTA_PAAAJORATOJEN_KAISTAJARJESTELYIHIN,
            LuokitteluType.KAISTAJARJESTELYN_PITUUS,
            LuokitteluType.KATULUOKKA,
            LuokitteluType.LIIKENNEMAARA
        ).forEachIndexed { i, luokitteluType ->
            val arvo = accessor.getInteger(i + 1)
            map[luokitteluType] = Luokittelutulos(luokitteluType, arvo, "")
        }
        return map
    }
}
