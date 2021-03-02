package fi.hel.haitaton.hanke.tormaystarkastelu


import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.domain.Hanke
import org.springframework.beans.factory.annotation.Autowired

open class TormaystarkasteluLaskentaServiceImpl(
    @Autowired private val hankeService: HankeService,
    @Autowired private val luokitteluService: TormaystarkasteluLuokitteluService
) : TormaystarkasteluLaskentaService {

    /**
     * Calculates new tormaystarkasteluTulos for hanke, if not yet existing
     */
    override fun calculateTormaystarkastelu(hankeTunnus: String): Hanke {
        // load data with hankeTunnus
        val hanke = hankeService.loadHanke(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)

        if (hanke.tilat.onTiedotLiikenneHaittaIndeksille) {

            // get rajaArvot for luokittelu
            //TODO some interface which can later be replaced with database calling.. this is now too hard coded?
            val rajaArvot = LuokitteluRajaArvot()

            // call service to get luokittelu with rajaArvot and hankeGeometries
            val luokittelutulos = luokitteluService.calculateTormaystarkasteluLuokitteluTulos(hanke, rajaArvot)

            // call something to create tormaystarkastelu with that luokittelu
            val laskentatulos =
                TormaystarkasteluCalculator.calculateAllIndeksit(initTormaystarkasteluTulos(hanke), luokittelutulos)

            // - save tormaysTulos to database //TODO
            // - call hankeservice to save liikennehaittaindeksi of tormaystulos to database //TODO
            // and saved hanke.onLiikenneHaittaIndeksi=true (or is it services job to do that?) //TODO

            // return hanke with tormaystulos
            hanke.tormaystarkasteluTulos = laskentatulos
        } else {
            //TODO: handle missing data situation? we can not calculate
        }

        // return hanke with tormaystulos
        return hanke
    }

    /**
     * Existing tormaystarkasteluTulos can be called with this
     */
    override fun getTormaystarkastelu(hankeTunnus: String): TormaystarkasteluTulos? {

        val hanke = hankeService.loadHanke(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)

        //we don't yet have tormaystarkastelu tulos
        if (!hanke.tilat.onLiikenneHaittaIndeksi) {
            return null
        }

        //TODO: get from database tormaystulokset
        // if we have tormaystarkastelu, return

        return initTormaystarkasteluTulos(hanke)
    }

    private fun initTormaystarkasteluTulos(hanke: Hanke): TormaystarkasteluTulos {
        if (hanke.hankeTunnus == null)
            throw IllegalArgumentException("hankeTunnus non existent when trying to get TormaysTarkastelu")

        val tormaysResults = TormaystarkasteluTulos(hanke.hankeTunnus!!)
        tormaysResults.hankeId = hanke.id!!//TODO: selvitettävä jostain


        //dummy code:
        tormaysResults.joukkoliikenneIndeksi = 2.4f
        tormaysResults.pyorailyIndeksi = 1.2f
        tormaysResults.perusIndeksi = 2.1f

        tormaysResults.liikennehaittaIndeksi = LiikennehaittaIndeksiType()
        tormaysResults.liikennehaittaIndeksi!!.indeksi = 1.2f
        tormaysResults.liikennehaittaIndeksi!!.type = IndeksiType.PYORAILYINDEKSI

        //TODO: in the future this would contain "Viereiset hankkeet" too

        return tormaysResults
    }

}
