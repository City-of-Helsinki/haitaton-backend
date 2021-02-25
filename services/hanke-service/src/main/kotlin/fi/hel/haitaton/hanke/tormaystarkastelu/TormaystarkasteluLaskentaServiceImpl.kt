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
            // call service paikkaService to get luokittelu with rajaArvot and hankeGeometries
            // - call something to create tormaystarkastelu with that luokittelu
            // - save tormaysTulos to database
            // - call hankeservice to save liikennehaittaindeksi of tormaystulos to database
                // and saved hanke.onLiikenneHaittaIndeksi=true (or is it services job to do that?)
            // - return hanke with tormaystulos


            // has saved tormaystulos to database
            // return hanke with tormaystulos

            // miten törmäystulos? Onko se erikseen hankkeella?
            // annetaanko TormaystarkasteluLaskentaService:lle koko hanke (tarvitsee  hankkeen tietoja) ja palauttaisiko se hankkeen, jota on
            // täydennetty törmäystuloksilla?
            //    hankeWithTormaysResults.liikennehaittaindeksi = dummyTulos.liikennehaittaIndeksi //tämä olisi hankkeelle tallennettavaa
            //    hankeWithTormaysResults.tormaystarkasteluTulos = dummyTulos  //tässä mukana myös liikennehaittaindeksi, mutta myös muu tulos

            hanke.tormaystarkasteluTulos = getDummyTormaystarkasteluTulos()    //TODO: replace with real implementation
        } else {
            //handle missing data situation?
        }

        // return hanke with tormaystulos
        return hanke
    }

    override fun getTormaystarkastelu(hankeTunnus: String): TormaystarkasteluTulos {
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
