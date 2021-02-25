package fi.hel.haitaton.hanke.tormaystarkastelu

class TormaystarkasteluTulos {
    var hankeId : Int = 0
    var liikennehaittaIndeksi: LiikennehaittaIndeksiType? = null
    var perusIndeksi: Double? = null
    var pyorailyIndeksi: Double? = null
    var joukkoliikenneIndeksi: Double? = null

}

class LiikennehaittaIndeksiType {

    var indeksi: Double = 0.0
    var type: IndeksiType? = null
}

enum class IndeksiType {
    PERUSINDEKSI,
    PYORAILYINDEKSI,
    JOUKKOLIIKENNEINDEKSI
}