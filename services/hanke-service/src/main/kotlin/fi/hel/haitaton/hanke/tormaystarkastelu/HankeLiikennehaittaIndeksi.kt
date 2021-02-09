package fi.hel.haitaton.hanke.tormaystarkastelu

data class HankeLiikennehaittaIndeksi(val hankeTunnus: String) {

    lateinit var tulosRivit: List<Luokittelutulos>

}
