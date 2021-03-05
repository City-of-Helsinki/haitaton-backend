package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.TormaysAnalyysiException
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.geometria.HankeGeometriatService
import org.springframework.beans.factory.annotation.Autowired

open class TormaystarkasteluLaskentaServiceImpl(
    @Autowired private val hankeService: HankeService,
    @Autowired private val luokitteluService: TormaystarkasteluLuokitteluService,
    @Autowired private val geometriatService: HankeGeometriatService
) : TormaystarkasteluLaskentaService {

    /**
     * Calculates new tormaystarkasteluTulos for hanke, if not yet existing
     */
    override fun calculateTormaystarkastelu(hankeTunnus: String): Hanke {
        // load data with hankeTunnus
        val hanke = hankeService.loadHanke(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)

        if (hanke.tilat.onLiikenneHaittaIndeksi) {
            throw TormaysAnalyysiException("Already has tormaysanalyysi calculated for $hankeTunnus")
        }

        if (hanke.tilat.onTiedotLiikenneHaittaIndeksille) {
            // load geometries for hanke to be able to classify the hits to traffic amount maps
            hanke.geometriat = geometriatService.loadGeometriat(hanke)

            // get rajaArvot for luokittelu
            // TODO some interface which can later be replaced with database calling.. this is now too hard coded?
            val rajaArvot = LuokitteluRajaArvot()

            // call service to get luokittelu with rajaArvot and hankeGeometries
            val luokittelutulos = luokitteluService.calculateTormaystarkasteluLuokitteluTulos(hanke, rajaArvot)

            // call something to create tormaystarkastelu with that luokittelu
            val laskentatulos =
                TormaystarkasteluCalculator.calculateAllIndeksit(initTormaystarkasteluTulos(hanke), luokittelutulos)

            hanke.tormaystarkasteluTulos = laskentatulos
            hanke.liikennehaittaindeksi = laskentatulos.liikennehaittaIndeksi
            hanke.tilat.onLiikenneHaittaIndeksi = true
            hankeService.updateHanke(hanke) // TODO updateHanke should save this new data? Or should it be a separate persistence service for that?
        } else {
            throw IllegalStateException("Hanke.tilat.onTiedotLiikenneHaittaIndeksille cannot be false here")
        }

        // return hanke with tormaystulos
        return hanke
    }

    /**
     * Existing tormaystarkasteluTulos can be called with this
     */
    override fun getTormaystarkastelu(hankeTunnus: String): TormaystarkasteluTulos? {

        val hanke = hankeService.loadHanke(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)

        // we don't yet have tormaystarkastelu tulos
        if (!hanke.tilat.onLiikenneHaittaIndeksi) {
            return null
        }

        // TODO: get from database tormaystulokset
        // if we have tormaystarkastelu, return

        return initTormaystarkasteluTulos(hanke)
    }

    private fun initTormaystarkasteluTulos(hanke: Hanke): TormaystarkasteluTulos {
        if (hanke.hankeTunnus == null) {
            throw IllegalArgumentException("hankeTunnus non existent when trying to get TormaysTarkastelu")
        }

        val laskentatulos = TormaystarkasteluTulos(hanke.hankeTunnus!!)
        laskentatulos.hankeId = hanke.id!!
        laskentatulos.hankeGeometriatId = hanke.geometriat!!.id!!
        // TODO: in the future this would contain "Viereiset hankkeet" too

        return laskentatulos
    }
}
