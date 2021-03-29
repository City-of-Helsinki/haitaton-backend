package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.TormaystarkasteluAlreadyCalculatedException
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.geometria.HankeGeometriatService
import org.springframework.beans.factory.annotation.Autowired

open class TormaystarkasteluLaskentaServiceImpl(
    @Autowired private val hankeService: HankeService,
    @Autowired private val luokitteluService: TormaystarkasteluLuokitteluService,
    @Autowired private val geometriatService: HankeGeometriatService,
    @Autowired private val tormaystarkasteluTulosRepository: TormaystarkasteluTulosRepository
) : TormaystarkasteluLaskentaService {

    /**
     * Calculates new tormaystarkasteluTulos for hanke, if not yet existing
     */
    override fun calculateTormaystarkastelu(hankeTunnus: String): Hanke {
        // load data with hankeTunnus
        val hanke = hankeService.loadHanke(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)

        if (hanke.tilat.onLiikenneHaittaIndeksi) {
            throw TormaystarkasteluAlreadyCalculatedException("Already has tormaysanalyysi calculated for $hankeTunnus")
        }

        if (hanke.tilat.onTiedotLiikenneHaittaIndeksille) {
            // load geometries for hanke to be able to classify the hits to traffic amount maps
            hanke.geometriat = geometriatService.loadGeometriat(hanke)

            // get rajaArvot for luokittelu
            // TODO some interface which can later be replaced with database calling
            val rajaArvot = LuokitteluRajaArvot()

            // call service to get luokittelu with rajaArvot and hankeGeometries
            val luokittelutulos = luokitteluService.calculateTormaystarkasteluLuokitteluTulos(hanke, rajaArvot)

            // call the calculator to create tormaystarkastelu with that luokittelu
            val laskentatulos =
                TormaystarkasteluCalculator.calculateAllIndeksit(initTormaystarkasteluTulos(hanke), luokittelutulos)

            // All values calculated, this particular result is now valid (VOIMASSA):
            laskentatulos.tila = TormaystarkasteluTulosTila.VOIMASSA

            // Apply the result to hanke:
            hankeService.applyAndSaveTormaystarkasteluTulos(hanke, laskentatulos)
        } else {
            throw IllegalStateException("Hanke.tilat.onTiedotLiikenneHaittaIndeksille cannot be false here; hanke $hankeTunnus")
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

        // TODO: this causes undesired extra db-traffic. In future each tulos should
        //  have its own related geometriaid (note, "geometria", not "geometriat" with end t)
        //  stored with the data in the database, so the injection, and thus this load
        //  will not be needed.
        // load geometries for hanke to be able to inject the geometriatid to tulos
        hanke.geometriat = geometriatService.loadGeometriat(hanke)

        // Only the HankeEntity has the list of tulos lazy-loaded; domain object does not (yet..)
        // So, loading the tulos via TormaystarkasteluRepository
        val entityList = tormaystarkasteluTulosRepository.findByHankeId(hanke.id!!)
        // Get the first (for now the only) entry from the list
        if (entityList.isEmpty()) {
            throw IllegalStateException("Hanke.tilat.onLiikenneHaittaIndeksi was true, but no results found in database; hanke $hankeTunnus")
        }
        val tormaystarkasteluTulosEntity = entityList[0]
        val tormaystarkasteluTulos = initTormaystarkasteluTulos(hanke)
        copyTormaystarkasteluTulosFromEntity(tormaystarkasteluTulosEntity, tormaystarkasteluTulos)

        return tormaystarkasteluTulos
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

    private fun copyTormaystarkasteluTulosFromEntity(
        tttEntity: TormaystarkasteluTulosEntity,
        ttt: TormaystarkasteluTulos
    ) {
        ttt.liikennehaittaIndeksi = tttEntity.liikennehaitta?.copy()
        ttt.perusIndeksi = tttEntity.perus
        ttt.pyorailyIndeksi = tttEntity.pyoraily
        ttt.joukkoliikenneIndeksi = tttEntity.joukkoliikenne
        ttt.tila = tttEntity.tila
        // TODO? domain object does not (yet?) have createdAt or tilaChangedAt fields?
        //  But are they even needed/used there (yet?)
    }

}
