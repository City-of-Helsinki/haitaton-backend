package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.roundToOneDecimal

object TormaystarkasteluCalculator {

    val perusindeksiPainot = mapOf(
        Pair(LuokitteluType.HAITTA_AJAN_KESTO, 0.1f),
        Pair(LuokitteluType.TODENNAKOINEN_HAITTA_PAAAJORATOJEN_KAISTAJARJESTELYIHIN, 0.25f),
        Pair(LuokitteluType.KAISTAJARJESTELYN_PITUUS, 0.2f),
        Pair(LuokitteluType.KATULUOKKA, 0.2f),
        Pair(LuokitteluType.LIIKENNEMAARA, 0.25f)
    )

    val pyorailyPainot = mutableListOf(
        TulosPainotus(arvo = 0, tulos = 1.0f, painotus = 1.0f),
        TulosPainotus(arvo = 1, tulos = 1.0f, painotus = 1.0f),
        TulosPainotus(arvo = 2, tulos = 1.0f, painotus = 1.0f),
        TulosPainotus(arvo = 3, tulos = 1.0f, painotus = 1.0f),
        // these actually matter
        TulosPainotus(arvo = 4, tulos = 3.0f, painotus = 1.0f),
        TulosPainotus(arvo = 5, tulos = 3.0f, painotus = 1.0f)
    )

    val bussiPainot = mutableListOf(
        TulosPainotus(arvo = 0, tulos = 1.0f, painotus = 1.0f),
        TulosPainotus(arvo = 1, tulos = 1.0f, painotus = 1.0f),
        TulosPainotus(arvo = 2, tulos = 1.0f, painotus = 1.0f),
        // these actually matter
        TulosPainotus(arvo = 3, tulos = 4.0f, painotus = 1.0f),
        TulosPainotus(arvo = 4, tulos = 4.0f, painotus = 1.0f),
        TulosPainotus(arvo = 5, tulos = 4.0f, painotus = 1.0f)
    )

    val raitiovaunuPainot = mutableListOf(
        TulosPainotus(arvo = 0, tulos = 1.0f, painotus = 1.0f),
        TulosPainotus(arvo = 1, tulos = 1.0f, painotus = 1.0f),
        TulosPainotus(arvo = 2, tulos = 1.0f, painotus = 1.0f),
        // these actually matter
        TulosPainotus(arvo = 3, tulos = 4.0f, painotus = 1.0f),
        TulosPainotus(arvo = 4, tulos = 4.0f, painotus = 1.0f),
        TulosPainotus(arvo = 5, tulos = 4.0f, painotus = 1.0f)
    )

    fun calculateAllIndeksit(
        tormaystarkasteluTulos: TormaystarkasteluTulos,
        luokittelutulos: Map<LuokitteluType, Luokittelutulos>
    ): TormaystarkasteluTulos {

        // all classification types has to be included
        if (luokittelutulos.keys.size != LuokitteluType.values().size) {
            throw IllegalArgumentException(
                "Missing classification types: ${LuokitteluType.values().toSet() - luokittelutulos.keys}")
        }

        calculatePerusIndeksi(
            luokittelutulos.getValue(LuokitteluType.HAITTA_AJAN_KESTO),
            luokittelutulos.getValue(LuokitteluType.TODENNAKOINEN_HAITTA_PAAAJORATOJEN_KAISTAJARJESTELYIHIN),
            luokittelutulos.getValue(LuokitteluType.KAISTAJARJESTELYN_PITUUS),
            luokittelutulos.getValue(LuokitteluType.KATULUOKKA),
            luokittelutulos.getValue(LuokitteluType.LIIKENNEMAARA),
            tormaystarkasteluTulos
        )

        calculatePyorailyIndeksi(
            luokittelutulos.getValue(LuokitteluType.PYORAILYN_PAAREITTI),
            tormaystarkasteluTulos
        )

        calculateJoukkoliikenneIndeksi(
            luokittelutulos.getValue(LuokitteluType.BUSSILIIKENNE),
            luokittelutulos.getValue(LuokitteluType.RAITIOVAUNULIIKENNE),
            tormaystarkasteluTulos
        )

        return tormaystarkasteluTulos
    }

    private fun calculatePerusIndeksi(
        haittaAjanKesto: Luokittelutulos,
        todennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin: Luokittelutulos,
        kaistajarjestelynPituus: Luokittelutulos,
        katuluokka: Luokittelutulos,
        liikennemaara: Luokittelutulos,
        tormaystarkasteluTulos: TormaystarkasteluTulos
    ) {
        var perusindeksi = haittaAjanKesto.arvo * perusindeksiPainot[LuokitteluType.HAITTA_AJAN_KESTO]!!
        perusindeksi += todennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.arvo *
                perusindeksiPainot[LuokitteluType.TODENNAKOINEN_HAITTA_PAAAJORATOJEN_KAISTAJARJESTELYIHIN]!!
        perusindeksi += kaistajarjestelynPituus.arvo * perusindeksiPainot[LuokitteluType.KAISTAJARJESTELYN_PITUUS]!!
        perusindeksi += katuluokka.arvo * perusindeksiPainot[LuokitteluType.KATULUOKKA]!!
        perusindeksi += liikennemaara.arvo * perusindeksiPainot[LuokitteluType.LIIKENNEMAARA]!!
        tormaystarkasteluTulos.perusIndeksi = perusindeksi.roundToOneDecimal()
    }

    private fun calculatePyorailyIndeksi(
        pyorailyLuokkaArvo: Luokittelutulos,
        tormaystarkasteluTulos: TormaystarkasteluTulos
    ) {
        val calculationRule = pyorailyPainot.firstOrNull { it.arvo == pyorailyLuokkaArvo.arvo }

        if (calculationRule == null) {
            tormaystarkasteluTulos.pyorailyIndeksi = 1.0f
        } else {
            tormaystarkasteluTulos.pyorailyIndeksi =
                calculationRule.tulos * calculationRule.painotus // TODO: 1 decimal precision?
        }
    }

    private fun calculateJoukkoliikenneIndeksi(
        bussiLuokkaArvo: Luokittelutulos,
        raitiovaunuLuokkaArvo: Luokittelutulos,
        tormaystarkasteluTulos: TormaystarkasteluTulos
    ) {
        val calculationRuleBussit = bussiPainot.firstOrNull { it.arvo == bussiLuokkaArvo.arvo }

        val calculationRuleRaitio = raitiovaunuPainot.firstOrNull { it.arvo == raitiovaunuLuokkaArvo.arvo }

        var bussiIndeksi = 1.0f
        if (calculationRuleBussit != null) {
            bussiIndeksi = calculationRuleBussit.tulos * calculationRuleBussit.painotus
        } // TODO: 1 decimal precision?

        var raitiovaunuIndeksi = 1.0f
        if (calculationRuleRaitio != null) {
            raitiovaunuIndeksi = calculationRuleRaitio.tulos * calculationRuleRaitio.painotus
        } // TODO: 1 decimal precision?

        // bigger of these matter so we will set that one
        tormaystarkasteluTulos.joukkoliikenneIndeksi = maxOf(bussiIndeksi, raitiovaunuIndeksi)
    }
}

data class TulosPainotus(val arvo: Int, val tulos: Float, val painotus: Float)
