package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.roundToOneDecimal

object TormaystarkasteluCalculator {

    private val perusindeksiPainot = mapOf(
        Pair(LuokitteluType.HAITTA_AJAN_KESTO, 0.1f),
        Pair(LuokitteluType.TODENNAKOINEN_HAITTA_PAAAJORATOJEN_KAISTAJARJESTELYIHIN, 0.25f),
        Pair(LuokitteluType.KAISTAJARJESTELYN_PITUUS, 0.2f),
        Pair(LuokitteluType.KATULUOKKA, 0.2f),
        Pair(LuokitteluType.LIIKENNEMAARA, 0.25f)
    )

    fun calculateAllIndeksit(luokittelutulos: Map<LuokitteluType, Luokittelutulos>): TormaystarkasteluTulos {
        // all classification types has to be included
        if (LuokitteluType.values().any { !luokittelutulos.containsKey(it) }) {
            throw IllegalArgumentException(
                "Missing classification types: ${LuokitteluType.values().toSet() - luokittelutulos.keys}")
        }

        val perus = perusindeksiPainot
                .map { (type, weight) -> luokittelutulos[type]!!.arvo * weight }
                .sum()
                .roundToOneDecimal()

        val pyoraily = if (luokittelutulos[LuokitteluType.PYORAILYN_PAAREITTI]!!.arvo >= 4) 3.0f else 1.0f

        val bussi = if (luokittelutulos[LuokitteluType.BUSSILIIKENNE]!!.arvo >= 3) 4.0f else 1.0f
        val raitiovaunu = if (luokittelutulos[LuokitteluType.RAITIOVAUNULIIKENNE]!!.arvo >= 3) 4.0f else 1.0f
        val joukko = maxOf(bussi, raitiovaunu)

        return TormaystarkasteluTulos(perus, pyoraily, joukko)
    }

}
