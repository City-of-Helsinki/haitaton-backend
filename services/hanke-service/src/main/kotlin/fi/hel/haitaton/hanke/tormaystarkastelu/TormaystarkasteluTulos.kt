package fi.hel.haitaton.hanke.tormaystarkastelu

data class TormaystarkasteluTulos(val hankeTunnus: String) {

    var hankeId: Int = 0
    var hankeGeometriatId: Int = 0
    var liikennehaittaIndeksi: LiikennehaittaIndeksiType? = null
    var perusIndeksi: Float? = null
    var pyorailyIndeksi: Float? = null
    var joukkoliikenneIndeksi: Float? = null

    override fun toString(): String {
        return "TormaystarkasteluTulos(" +
                "hankeTunnus='$hankeTunnus', " +
                "hankeId=$hankeId, " +
                "hankeGeometriatId=$hankeGeometriatId, " +
                "liikennehaittaIndeksi=$liikennehaittaIndeksi, " +
                "perusIndeksi=$perusIndeksi, " +
                "pyorailyIndeksi=$pyorailyIndeksi, " +
                "joukkoliikenneIndeksi=$joukkoliikenneIndeksi)"
    }

    /**
     * Selects maximum of three indeksi and sets that to be LiikennehaittaIndeksi.
     */
    fun calculateLiikennehaittaIndeksi() {
        val maxIndex = setOf(perusIndeksi!!, pyorailyIndeksi!!, joukkoliikenneIndeksi!!).maxOrNull()
            ?: throw IllegalStateException("perusIndeksi, pyorailyIndeksi and joukkoliikenneIndeksi cannot be null")
        liikennehaittaIndeksi = when (maxIndex) {
            joukkoliikenneIndeksi -> LiikennehaittaIndeksiType(
                joukkoliikenneIndeksi!!,
                IndeksiType.JOUKKOLIIKENNEINDEKSI
            )
            perusIndeksi -> LiikennehaittaIndeksiType(perusIndeksi!!, IndeksiType.PERUSINDEKSI)
            else -> LiikennehaittaIndeksiType(pyorailyIndeksi!!, IndeksiType.PYORAILYINDEKSI)
        }
    }
}

data class LiikennehaittaIndeksiType(var indeksi: Float, var type: IndeksiType)

enum class IndeksiType {
    PERUSINDEKSI,
    PYORAILYINDEKSI,
    JOUKKOLIIKENNEINDEKSI
}
