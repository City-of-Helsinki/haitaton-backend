package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.geometria.HankeGeometriat
import fi.hel.haitaton.hanke.roundToOneDecimal
import org.springframework.beans.factory.annotation.Autowired

open class TormaystarkasteluLaskentaService(
        @Autowired private val luokitteluRajaArvotService: LuokitteluRajaArvotService,
        @Autowired private val perusIndeksiPainotService: PerusIndeksiPainotService,
        @Autowired private val tormaysService: TormaystarkasteluTormaysService
) {

    fun calculateTormaystarkastelu(hanke: Hanke): TormaystarkasteluTulos? {
        if (!hasAllRequiredInformation(hanke)) {
            return null
        }

        val perusIndeksi = calculatePerusIndeksi(hanke)

        val pyorailyLuokittelu = pyorailyLuokittelu(hanke.geometriat!!)
        val pyorailyIndeksi = if (pyorailyLuokittelu >= 4) 3.0f else 1.0f

        val bussiLuokittelu = bussiLuokittelu(hanke.geometriat!!)
        val bussiIndeksi = if (bussiLuokittelu >= 3) 4.0f else 1.0f

        val raitiovaunuLuokittelu = raitiovaunuLuokittelu(hanke.geometriat!!)
        val raitiovaunuIndeksi = if (raitiovaunuLuokittelu >= 3) 4.0f else 1.0f

        val joukkoliikenneIndeksi = maxOf(bussiIndeksi, raitiovaunuIndeksi)

        return TormaystarkasteluTulos(perusIndeksi, pyorailyIndeksi, joukkoliikenneIndeksi)
    }

    private fun hasAllRequiredInformation(hanke: Hanke): Boolean {
        return (hanke.hankeTunnus != null
                && hanke.alkuPvm != null
                && hanke.loppuPvm != null
                && hanke.kaistaHaitta != null
                && hanke.kaistaPituusHaitta != null
                && hanke.geometriat != null
                && hanke.geometriat?.id != null)
    }

    private fun calculatePerusIndeksi(hanke: Hanke): Float {
        val luokittelu = mutableMapOf<LuokitteluType, Int>()

        luokittelu[LuokitteluType.HAITTA_AJAN_KESTO] = haittaAjanKestoLuokittelu(hanke.haittaAjanKestoDays!!)
        luokittelu[LuokitteluType.TODENNAKOINEN_HAITTA_PAAAJORATOJEN_KAISTAJARJESTELYIHIN] = hanke.kaistaHaitta!!.value
        luokittelu[LuokitteluType.KAISTAJARJESTELYN_PITUUS] = hanke.kaistaPituusHaitta!!.value

        val katuluokkaLuokittelu = katuluokkaLuokittelu(hanke.geometriat!!)
        luokittelu[LuokitteluType.KATULUOKKA] = katuluokkaLuokittelu
        luokittelu[LuokitteluType.LIIKENNEMAARA] = liikennemaaraLuokittelu(hanke.geometriat!!, katuluokkaLuokittelu)

        return calculatePerusIndeksiFromLuokittelu(luokittelu)
    }

    fun haittaAjanKestoLuokittelu(haittaAjanKestoDays: Int) =
            luokitteluRajaArvotService.getHaittaAjanKestoLuokka(haittaAjanKestoDays)

    private fun katuluokkaLuokittelu(hankeGeometriat: HankeGeometriat): Int {
        if (tormaysService.anyIntersectsYleinenKatuosa(hankeGeometriat)) {
            // ON ylre_parts => street_classes?
            return tormaysService.maxIntersectingLiikenteellinenKatuluokka(hankeGeometriat)
                    // EI street_classes => ylre_classes?
                    ?: tormaysService.maxIntersectingYleinenkatualueKatuluokka(hankeGeometriat)
                    // EI ylre_classes
                    ?: 0
        } else {
            // EI ylre_parts => ylre_classes?
            val max = tormaysService.maxIntersectingYleinenkatualueKatuluokka(hankeGeometriat)
                    // EI ylre_classes
                    ?: return 0
            // ON ylre_classes => street_classes?
            return tormaysService.maxIntersectingLiikenteellinenKatuluokka(hankeGeometriat)
                    // JOS EI LÖYDY => Valitse ylre_classes
                    ?: max
        }
    }

    private fun liikennemaaraLuokittelu(hankeGeometriat: HankeGeometriat, katuluokkaLuokittelu: Int): Int {
        if (katuluokkaLuokittelu == 0) {
            return 0
        }
        // type of street (=street class) decides which volume data we use for trafic (buffering of street width varies)
        val radius = if (katuluokkaLuokittelu >= 4) {
            TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_30
        } else {
            TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_15
        }
        val maxVolume = tormaysService.maxLiikennemaara(hankeGeometriat, radius) ?: 0
        return luokitteluRajaArvotService.getLiikennemaaraLuokka(maxVolume)
    }

    fun calculatePerusIndeksiFromLuokittelu(luokitteluByType: Map<LuokitteluType, Int>): Float =
            perusIndeksiPainotService.getAll()
                    .map { (type, weight) -> luokitteluByType[type]!! * weight }
                    .sum()
                    .roundToOneDecimal()

    private fun pyorailyLuokittelu(hankeGeometriat: HankeGeometriat): Int {
        return when {
            tormaysService.anyIntersectsWithCyclewaysPriority(hankeGeometriat) ->
                PyorailyTormaysLuokittelu.PRIORISOITU_REITTI
            tormaysService.anyIntersectsWithCyclewaysMain(hankeGeometriat) ->
                PyorailyTormaysLuokittelu.PAAREITTI
            else ->
                PyorailyTormaysLuokittelu.EI_PYORAILUREITTI
        }.value
    }

    fun bussiLuokittelu(hankeGeometriat: HankeGeometriat): Int {
        if (tormaysService.anyIntersectsCriticalBusRoutes(hankeGeometriat)) {
            return BussiLiikenneLuokittelu.KAMPPI_RAUTATIENTORI.value
        }

        val bussesTormaystulos = tormaysService.getIntersectingBusRoutes(hankeGeometriat)

        if (bussesTormaystulos.isEmpty()) {
            return BussiLiikenneLuokittelu.EI_VAIKUTA.value
        }

        val countOfRushHourBuses = bussesTormaystulos.sumOf { it.vuoromaaraRuuhkatunnissa }
        val valueByRajaArvo = luokitteluRajaArvotService.getBussiLiikenneRuuhkaLuokka(countOfRushHourBuses)

        val valueByRunkolinja = when {
            bussesTormaystulos.any { it.runkolinja == TormaystarkasteluBussiRunkolinja.ON } ->
                BussiLiikenneLuokittelu.RUNKOLINJA
            bussesTormaystulos.any { it.runkolinja == TormaystarkasteluBussiRunkolinja.LAHES } ->
                BussiLiikenneLuokittelu.RUNKOLINJAMAINEN
            else ->
                BussiLiikenneLuokittelu.PERUS
        }.value

        return maxOf(valueByRajaArvo, valueByRunkolinja)
    }

    private fun raitiovaunuLuokittelu(hankeGeometriat: HankeGeometriat) =
            tormaysService.maxIntersectingTramByLaneType(hankeGeometriat) ?: 0

}
