package fi.hel.haitaton.hanke.tormaystarkastelu


import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.domain.Hanke
import org.springframework.beans.factory.annotation.Autowired

open class TormaystarkasteluLaskentaServiceImpl(
    @Autowired private val hankeService: HankeService,
    @Autowired private val paikkaService: TormaystarkasteluPaikkaService
) : TormaystarkasteluLaskentaService {

    override fun calculateTormaystarkastelu(hankeTunnus: String): Hanke {
        // load data with hankeTunnus
        val hanke = hankeService.loadHanke(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)

        if (hanke.tilat.onTiedotLiikenneHaittaIndeksille) {
            // then calculate:
            // get rajaArvot for luokittelu
            //TODO some interface which can later be replaced with database calling.. this is now too hard coded?
            val rajaArvot = LuokitteluRajaArvot()
            // call service paikkaService to get luokittelu with rajaArvot and hankeGeometries
            val luokittelutulos = paikkaService.calculateTormaystarkasteluLuokitteluTulos(hanke, rajaArvot)
            // - call something to create tormaystarkastelu with that luokittelu //TODO
            // - save tormaysTulos to database //TODO
            // - call hankeservice to save liikennehaittaindeksi of tormaystulos to database //TODO
            // and saved hanke.onLiikenneHaittaIndeksi=true (or is it services job to do that?) //TODO:
            // - return hanke with tormaystulos //TODO

            hanke.tormaystarkasteluTulos = getDummyTormaystarkasteluTulos()    //TODO: replace with real implementation
        } else {
            //TODO: handle missing data situation? we can not calculate
        }

        // return hanke with tormaystulos
        return hanke
    }

    override fun getTormaystarkastelu(hankeTunnus: String): TormaystarkasteluTulos? {

        val hanke = hankeService.loadHanke(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)

        if (!hanke.tilat.onLiikenneHaittaIndeksi) {
            return null
        }
        //check if hanke.tilaOn -> tormaystarkastelu not exists -> return null
        //get from database tormays
        // if we have tormaystarkastelu, return

        // what if we don't have it?

        return getDummyTormaystarkasteluTulos()
    }

    private fun getDummyTormaystarkasteluTulos(): TormaystarkasteluTulos {
        val tormaysResults = TormaystarkasteluTulos("TODOHANKE")
        tormaysResults.hankeId = 2 //TODO: selvitettävä jostain
        tormaysResults.joukkoliikenneIndeksi = 3.4f
        tormaysResults.pyorailyIndeksi = 4.2f
        tormaysResults.perusIndeksi = 2.1f

        tormaysResults.liikennehaittaIndeksi = LiikennehaittaIndeksiType()
        tormaysResults.liikennehaittaIndeksi!!.indeksi = 4.2f
        tormaysResults.liikennehaittaIndeksi!!.type = IndeksiType.PYORAILYINDEKSI

        //TODO: in the future this would contain "Viereiset hankkeet" too

        return tormaysResults
    }

}
