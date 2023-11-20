package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.domain.Hankealue
import fi.hel.haitaton.hanke.domain.alkuPvm
import fi.hel.haitaton.hanke.domain.geometriat
import fi.hel.haitaton.hanke.domain.haittaAjanKestoDays
import fi.hel.haitaton.hanke.domain.kaistaHaitat
import fi.hel.haitaton.hanke.domain.kaistaPituusHaitat
import fi.hel.haitaton.hanke.domain.loppuPvm
import fi.hel.haitaton.hanke.roundToOneDecimal
import kotlin.math.max
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class TormaystarkasteluLaskentaService(
    @Autowired private val tormaysService: TormaystarkasteluTormaysService
) {

    fun calculateTormaystarkastelu(
        alueet: List<Hankealue>,
        geometriaIds: Set<Int>
    ): TormaystarkasteluTulos? {
        if (!hasAllRequiredInformation(alueet)) {
            return null
        }

        val perusIndeksi = calculatePerusIndeksi(alueet, geometriaIds)

        val pyorailyLuokittelu = pyorailyLuokittelu(geometriaIds)
        val pyorailyIndeksi = if (pyorailyLuokittelu >= 4) 3.0f else 1.0f

        val bussiLuokittelu = bussiLuokittelu(geometriaIds)
        val bussiIndeksi = if (bussiLuokittelu >= 3) 4.0f else 1.0f

        val raitiovaunuLuokittelu = raitiovaunuLuokittelu(geometriaIds)
        val raitiovaunuIndeksi = if (raitiovaunuLuokittelu >= 3) 4.0f else 1.0f

        return TormaystarkasteluTulos(
            perusIndeksi,
            pyorailyIndeksi,
            bussiIndeksi,
            raitiovaunuIndeksi,
        )
    }

    private fun hasAllRequiredInformation(alueet: List<Hankealue>): Boolean {
        return (alueet.alkuPvm() != null &&
            alueet.loppuPvm() != null &&
            alueet.kaistaHaitat().isNotEmpty() &&
            alueet.kaistaPituusHaitat().isNotEmpty() &&
            alueet.geometriat().isNotEmpty())
    }

    private fun calculatePerusIndeksi(alueet: List<Hankealue>, geometriaIds: Set<Int>): Float {
        val luokittelu = mutableMapOf<LuokitteluType, Int>()

        luokittelu[LuokitteluType.HAITTA_AJAN_KESTO] =
            RajaArvoLuokittelija.getHaittaAjanKestoLuokka(alueet.haittaAjanKestoDays()!!)
        luokittelu[LuokitteluType.TODENNAKOINEN_HAITTA_PAAAJORATOJEN_KAISTAJARJESTELYIHIN] =
            alueet.kaistaHaitat().maxOfOrNull { it.value }!!
        luokittelu[LuokitteluType.KAISTAJARJESTELYN_PITUUS] =
            alueet.kaistaPituusHaitat().maxOfOrNull { it.value }!!

        val katuluokkaLuokittelu = katuluokkaLuokittelu(geometriaIds)
        luokittelu[LuokitteluType.KATULUOKKA] = katuluokkaLuokittelu
        luokittelu[LuokitteluType.LIIKENNEMAARA] =
            liikennemaaraLuokittelu(geometriaIds, katuluokkaLuokittelu)

        return calculatePerusIndeksiFromLuokittelu(luokittelu)
    }

    internal fun katuluokkaLuokittelu(geometriaIds: Set<Int>): Int {
        return if (tormaysService.anyIntersectsYleinenKatuosa(geometriaIds)) {
            // ON ylre_parts => street_classes?
            tormaysService.maxIntersectingLiikenteellinenKatuluokka(geometriaIds)
                // EI street_classes => ylre_classes?
                ?: tormaysService.maxIntersectingYleinenkatualueKatuluokka(geometriaIds)
                // EI ylre_classes
                ?: 0
        } else {
            // EI ylre_parts => ylre_classes?
            val max =
                tormaysService.maxIntersectingYleinenkatualueKatuluokka(geometriaIds)
                    // EI ylre_classes
                    ?: return 0
            // ON ylre_classes => street_classes?
            tormaysService.maxIntersectingLiikenteellinenKatuluokka(geometriaIds)
                // JOS EI LÖYDY => Valitse ylre_classes
                ?: max
        }
    }

    internal fun liikennemaaraLuokittelu(geometriaIds: Set<Int>, katuluokkaLuokittelu: Int): Int {
        if (katuluokkaLuokittelu == 0) {
            return 0
        }
        // type of street (=street class) decides which volume data we use for traffic (buffering of
        // street width varies)
        val radius =
            if (katuluokkaLuokittelu >= 4) {
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_30
            } else {
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_15
            }
        val maxVolume = tormaysService.maxLiikennemaara(geometriaIds, radius) ?: 0
        return RajaArvoLuokittelija.getLiikennemaaraLuokka(maxVolume)
    }

    internal fun calculatePerusIndeksiFromLuokittelu(
        luokitteluByType: Map<LuokitteluType, Int>
    ): Float =
        perusIndeksiPainot
            .map { (type, weight) -> luokitteluByType[type]!! * weight }
            .sum()
            .roundToOneDecimal()

    internal fun pyorailyLuokittelu(geometriaIds: Set<Int>): Int =
        when {
            tormaysService.anyIntersectsWithCyclewaysPriority(geometriaIds) ->
                PyorailyTormaysLuokittelu.PRIORISOITU_REITTI
            tormaysService.anyIntersectsWithCyclewaysMain(geometriaIds) ->
                PyorailyTormaysLuokittelu.PAAREITTI
            else -> PyorailyTormaysLuokittelu.EI_PYORAILUREITTI
        }.value

    internal fun bussiLuokittelu(geometriaIds: Set<Int>): Int {
        if (tormaysService.anyIntersectsCriticalBusRoutes(geometriaIds)) {
            return BussiLiikenneLuokittelu.KAMPPI_RAUTATIENTORI.value
        }

        val bussireitit = tormaysService.getIntersectingBusRoutes(geometriaIds)

        val valueByRunkolinja =
            bussireitit.maxOfOrNull { it.runkolinja.toBussiLiikenneLuokittelu().value }
                // bussireitit is empty
                ?: return BussiLiikenneLuokittelu.EI_VAIKUTA.value

        val countOfRushHourBuses = bussireitit.sumOf { it.vuoromaaraRuuhkatunnissa }
        val valueByRajaArvo =
            RajaArvoLuokittelija.getBussiLiikenneRuuhkaLuokka(countOfRushHourBuses)

        return max(valueByRajaArvo, valueByRunkolinja)
    }

    private fun raitiovaunuLuokittelu(geometriaIds: Set<Int>) =
        tormaysService.maxIntersectingTramByLaneType(geometriaIds) ?: 0
}
