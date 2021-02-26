package fi.hel.haitaton.hanke.tormaystarkastelu

data class TormaystarkasteluTulos (val hankeTunnus:String){

    var hankeId : Int = 0
    var liikennehaittaIndeksi: LiikennehaittaIndeksiType? = null
    var perusIndeksi: Float? = null
    var pyorailyIndeksi: Float? = null
    var joukkoliikenneIndeksi: Float? = null

}

class LiikennehaittaIndeksiType {

    var indeksi: Float = 0.0f
    var type: IndeksiType? = null
}

enum class IndeksiType {
    PERUSINDEKSI,
    PYORAILYINDEKSI,
    JOUKKOLIIKENNEINDEKSI
}
