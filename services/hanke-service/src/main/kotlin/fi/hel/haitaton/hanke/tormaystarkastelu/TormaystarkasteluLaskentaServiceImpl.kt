package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.geometria.HankeGeometriatService
import org.springframework.beans.factory.annotation.Autowired

open class TormaystarkasteluLaskentaServiceImpl(
    @Autowired private val luokitteluService: TormaystarkasteluLuokitteluService
) : TormaystarkasteluLaskentaService {

    private fun hasAllRequiredInformation(hanke: Hanke): Boolean {
        return (hanke.hankeTunnus != null
                && hanke.alkuPvm != null
                && hanke.loppuPvm != null
                && hanke.kaistaHaitta != null
                && hanke.kaistaPituusHaitta != null
                && hanke.geometriat != null)
    }

    override fun calculateTormaystarkastelu(hanke: Hanke): TormaystarkasteluTulos? {
        if (!hasAllRequiredInformation(hanke)) {
            return null
        }

        // get rajaArvot for luokittelu
        // TODO some interface which can later be replaced with database calling
        val rajaArvot = LuokitteluRajaArvot()

        val luokittelutulos = luokitteluService.calculateTormaystarkasteluLuokitteluTulos(hanke, rajaArvot)
        val laskentatulos = TormaystarkasteluCalculator.calculateAllIndeksit(initTormaystarkasteluTulos(hanke), luokittelutulos)

        laskentatulos.tila = TormaystarkasteluTulosTila.VOIMASSA

        return laskentatulos
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
