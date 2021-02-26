package fi.hel.haitaton.hanke.tormaystarkastelu

class TormaystarkasteluCalculator {

    val pyorailyPainot = mutableListOf(
        TulosPainotus(arvo = 0, tulos = 1.0f, painotus = 1.0f),
        TulosPainotus(arvo = 1, tulos = 1.0f, painotus = 1.0f),
        TulosPainotus(arvo = 2, tulos = 1.0f, painotus = 1.0f),
        TulosPainotus(arvo = 3, tulos = 1.0f, painotus = 1.0f),
        //these actually matter
        TulosPainotus(arvo = 4, tulos = 3.0f, painotus = 1.0f),
        TulosPainotus(arvo = 5, tulos = 3.0f, painotus = 1.0f)
    )

    val bussiPainot = mutableListOf(
        TulosPainotus(arvo = 0, tulos = 1.0f, painotus = 1.0f),
        TulosPainotus(arvo = 1, tulos = 1.0f, painotus = 1.0f),
        TulosPainotus(arvo = 2, tulos = 1.0f, painotus = 1.0f),
        //these actually matter
        TulosPainotus(arvo = 3, tulos = 4.0f, painotus = 1.0f),
        TulosPainotus(arvo = 4, tulos = 4.0f, painotus = 1.0f),
        TulosPainotus(arvo = 5, tulos = 4.0f, painotus = 1.0f)
    )

    val raitiovaunuPainot = mutableListOf(
        TulosPainotus(arvo = 0, tulos = 1.0f, painotus = 1.0f),
        TulosPainotus(arvo = 1, tulos = 1.0f, painotus = 1.0f),
        TulosPainotus(arvo = 2, tulos = 1.0f, painotus = 1.0f),
        //these actually matter
        TulosPainotus(arvo = 3, tulos = 4.0f, painotus = 1.0f),
        TulosPainotus(arvo = 4, tulos = 4.0f, painotus = 1.0f),
        TulosPainotus(arvo = 5, tulos = 4.0f, painotus = 1.0f)
    )

    fun calculateAllIndeksit(
        newTormaystarkasteluTulos: TormaystarkasteluTulos,
        luokittelutulos: List<Luokittelutulos>
    ): TormaystarkasteluTulos {

        //TODO: call  calculatePerusIndeksi(luokittelutulos, newTormaystarkasteluTulos)
        calculatePyorailyIndeksi(luokittelutulos, newTormaystarkasteluTulos)
        calculateJoukkoliikenneIndeksi(luokittelutulos, newTormaystarkasteluTulos)
        return newTormaystarkasteluTulos
    }

    private fun calculateJoukkoliikenneIndeksi(
        luokittelutulos: List<Luokittelutulos>,
        newTormaystarkasteluTulos: TormaystarkasteluTulos
    ) {
        val bussiLuokkaArvo =
            luokittelutulos.first { lt -> lt.luokitteluType == LuokitteluType.BUSSILIIKENNE }

        val raitiovaunuLuokkaArvo =
            luokittelutulos.first { lt -> lt.luokitteluType == LuokitteluType.RAITIOVAUNULIIKENNE }


        val calculationRuleBussit = bussiPainot.firstOrNull { it.arvo == bussiLuokkaArvo.arvo }

        val calculationRuleRaitio = raitiovaunuPainot.firstOrNull { it.arvo == raitiovaunuLuokkaArvo.arvo }

        var bussiIndeksi = 1.0f
        if (calculationRuleBussit != null) {
            bussiIndeksi = calculationRuleBussit.tulos * calculationRuleBussit.painotus
        } //TODO: 1 decimal precision?

        var raitiovaunuIndeksi = 1.0f
        if (calculationRuleRaitio != null) {
            raitiovaunuIndeksi = calculationRuleRaitio.tulos * calculationRuleRaitio.painotus
        } //TODO: 1 decimal precision?

        //bigger of these matter so we will set that one
        newTormaystarkasteluTulos.joukkoliikenneIndeksi = maxOf(bussiIndeksi, raitiovaunuIndeksi)
    }

    private fun calculatePerusIndeksi(
        luokittelutulos: List<Luokittelutulos>,
        newTormaystarkasteluTulos: TormaystarkasteluTulos
    ) {
        TODO("Not yet implemented")
    }

    private fun calculatePyorailyIndeksi(
        luokittelutulos: List<Luokittelutulos>,
        newTormaystarkasteluTulos: TormaystarkasteluTulos
    ) {
        val pyorailyLuokkaArvo =
            luokittelutulos.first { lt -> lt.luokitteluType == LuokitteluType.PYORAILYN_PAAREITTI }

        val calculationRule = pyorailyPainot.firstOrNull { it.arvo == pyorailyLuokkaArvo.arvo }

        if (calculationRule == null) {
            newTormaystarkasteluTulos.pyorailyIndeksi = 1.0f

        } else {
            if (calculationRule != null) {
                newTormaystarkasteluTulos.pyorailyIndeksi = calculationRule.tulos * calculationRule.painotus
            } //TODO: 1 decimal precision?
        }
    }
}


class TulosPainotus(val arvo: Int, val tulos: Float, val painotus: Float)
