package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.domain.Hanke
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
        return TormaystarkasteluCalculator.calculateAllIndeksit(luokittelutulos)
    }

}
