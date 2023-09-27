package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.geometria.Geometriat
import fi.hel.haitaton.hanke.roundToOneDecimal
import org.springframework.beans.factory.annotation.Autowired

open class TormaystarkasteluLaskentaService(
    @Autowired private val tormaysService: TormaystarkasteluTormaysService
) {

    fun calculateTormaystarkastelu(hanke: Hanke): TormaystarkasteluTulos? {
        if (!hasAllRequiredInformation(hanke)) {
            return null
        }

        val perusIndeksi = calculatePerusIndeksi(hanke)

        val pyorailyLuokittelu = pyorailyLuokittelu(hanke.alueidenGeometriat())
        val pyorailyIndeksi = if (pyorailyLuokittelu >= 4) 3.0f else 1.0f

        val bussiLuokittelu = bussiLuokittelu(hanke.alueidenGeometriat())
        val bussiIndeksi = if (bussiLuokittelu >= 3) 4.0f else 1.0f

        val raitiovaunuLuokittelu = raitiovaunuLuokittelu(hanke.alueidenGeometriat())
        val raitiovaunuIndeksi = if (raitiovaunuLuokittelu >= 3) 4.0f else 1.0f

        val joukkoliikenneIndeksi = maxOf(bussiIndeksi, raitiovaunuIndeksi)

        return TormaystarkasteluTulos(perusIndeksi, pyorailyIndeksi, joukkoliikenneIndeksi)
    }

    private fun hasAllRequiredInformation(hanke: Hanke): Boolean {
        return (hanke.hankeTunnus != null &&
            hanke.alkuPvm != null &&
            hanke.loppuPvm != null &&
            hanke.kaistaHaitat().isNotEmpty() &&
            hanke.kaistaPituusHaitat().isNotEmpty() &&
            hanke.alueidenGeometriat().isNotEmpty())
    }

    private fun calculatePerusIndeksi(hanke: Hanke): Float {
        val luokittelu = mutableMapOf<LuokitteluType, Int>()

        luokittelu[LuokitteluType.HAITTA_AJAN_KESTO] =
            haittaAjanKestoLuokittelu(hanke.haittaAjanKestoDays!!)
        luokittelu[LuokitteluType.TODENNAKOINEN_HAITTA_PAAAJORATOJEN_KAISTAJARJESTELYIHIN] =
            hanke.kaistaHaitat().maxOfOrNull { it.value }!!
        luokittelu[LuokitteluType.KAISTAJARJESTELYN_PITUUS] =
            hanke.kaistaPituusHaitat().maxOfOrNull { it.value }!!

        val katuluokkaLuokittelu = katuluokkaLuokittelu(hanke.alueidenGeometriat())
        luokittelu[LuokitteluType.KATULUOKKA] = katuluokkaLuokittelu
        luokittelu[LuokitteluType.LIIKENNEMAARA] =
            liikennemaaraLuokittelu(hanke.alueidenGeometriat(), katuluokkaLuokittelu)

        return calculatePerusIndeksiFromLuokittelu(luokittelu)
    }

    fun haittaAjanKestoLuokittelu(haittaAjanKestoDays: Int) =
        LuokitteluRajaArvotService.getHaittaAjanKestoLuokka(haittaAjanKestoDays)

    private fun katuluokkaLuokittelu(geometriat: List<Geometriat>): Int {
        if (tormaysService.anyIntersectsYleinenKatuosa(geometriat)) {
            // ON ylre_parts => street_classes?
            return tormaysService.maxIntersectingLiikenteellinenKatuluokka(geometriat)
            // EI street_classes => ylre_classes?
            ?: tormaysService.maxIntersectingYleinenkatualueKatuluokka(geometriat)
                // EI ylre_classes
                ?: 0
        } else {
            // EI ylre_parts => ylre_classes?
            val max =
                tormaysService.maxIntersectingYleinenkatualueKatuluokka(geometriat)
                // EI ylre_classes
                ?: return 0
            // ON ylre_classes => street_classes?
            return tormaysService.maxIntersectingLiikenteellinenKatuluokka(geometriat)
            // JOS EI LÖYDY => Valitse ylre_classes
            ?: max
        }
    }

    private fun liikennemaaraLuokittelu(
        geometriat: List<Geometriat>,
        katuluokkaLuokittelu: Int
    ): Int {
        if (katuluokkaLuokittelu == 0) {
            return 0
        }
        // type of street (=street class) decides which volume data we use for trafic (buffering of
        // street width varies)
        val radius =
            if (katuluokkaLuokittelu >= 4) {
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_30
            } else {
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_15
            }
        val maxVolume = tormaysService.maxLiikennemaara(geometriat, radius) ?: 0
        return LuokitteluRajaArvotService.getLiikennemaaraLuokka(maxVolume)
    }

    fun calculatePerusIndeksiFromLuokittelu(luokitteluByType: Map<LuokitteluType, Int>): Float =
        perusIndeksiPainot
            .map { (type, weight) -> luokitteluByType[type]!! * weight }
            .sum()
            .roundToOneDecimal()

    private fun pyorailyLuokittelu(geometriat: List<Geometriat>): Int {
        return when {
            tormaysService.anyIntersectsWithCyclewaysPriority(geometriat) ->
                PyorailyTormaysLuokittelu.PRIORISOITU_REITTI
            tormaysService.anyIntersectsWithCyclewaysMain(geometriat) ->
                PyorailyTormaysLuokittelu.PAAREITTI
            else -> PyorailyTormaysLuokittelu.EI_PYORAILUREITTI
        }.value
    }

    fun bussiLuokittelu(geometriat: List<Geometriat>): Int {
        if (tormaysService.anyIntersectsCriticalBusRoutes(geometriat)) {
            return BussiLiikenneLuokittelu.KAMPPI_RAUTATIENTORI.value
        }

        val bussesTormaystulos = tormaysService.getIntersectingBusRoutes(geometriat)

        if (bussesTormaystulos.isEmpty()) {
            return BussiLiikenneLuokittelu.EI_VAIKUTA.value
        }

        val countOfRushHourBuses = bussesTormaystulos.sumOf { it.vuoromaaraRuuhkatunnissa }
        val valueByRajaArvo =
            LuokitteluRajaArvotService.getBussiLiikenneRuuhkaLuokka(countOfRushHourBuses)

        val valueByRunkolinja =
            when {
                bussesTormaystulos.any { it.runkolinja == TormaystarkasteluBussiRunkolinja.ON } ->
                    BussiLiikenneLuokittelu.RUNKOLINJA
                bussesTormaystulos.any {
                    it.runkolinja == TormaystarkasteluBussiRunkolinja.LAHES
                } -> BussiLiikenneLuokittelu.RUNKOLINJAMAINEN
                else -> BussiLiikenneLuokittelu.PERUS
            }.value

        return maxOf(valueByRajaArvo, valueByRunkolinja)
    }

    private fun raitiovaunuLuokittelu(geometriat: List<Geometriat>) =
        tormaysService.maxIntersectingTramByLaneType(geometriat) ?: 0
}
