package fi.hel.haitaton.hanke.tormaystarkastelu

class TormaystarkasteluCalculator {

    val pyorailyPainot = mutableListOf(
        TulosPainotus(arvo = 0, tulos = 1.0f, painotus = 1.0f), //also everything else interpreted with this
        TulosPainotus(arvo = 4, tulos = 3.0f, painotus = 1.0f),
        TulosPainotus(arvo = 5, tulos = 3.0f, painotus = 1.0f)
    )

    fun calculateAllIndeksit(
        newTormaystarkasteluTulos: TormaystarkasteluTulos,
        luokittelutulos: List<Luokittelutulos>
    ): TormaystarkasteluTulos {

        //TODO: call  calculatePerusIndeksi(luokittelutulos, newTormaystarkasteluTulos)
        calculatePyorailyIndeksi(luokittelutulos, newTormaystarkasteluTulos)
        //TODO: call   calculateJoukkoliikenneIndeksi(luokittelutulos, newTormaystarkasteluTulos)
        return newTormaystarkasteluTulos
    }

    private fun calculateJoukkoliikenneIndeksi(
        luokittelutulos: List<Luokittelutulos>,
        newTormaystarkasteluTulos: TormaystarkasteluTulos
    ) {
        TODO("Not yet implemented")
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
            } //TODO: 2 decimal precision?
        }
    }
}


class TulosPainotus(val arvo: Int, val tulos: Float, val painotus: Float)
