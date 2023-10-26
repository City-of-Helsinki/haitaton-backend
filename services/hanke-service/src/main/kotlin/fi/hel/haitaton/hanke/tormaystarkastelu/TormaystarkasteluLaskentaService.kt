package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.domain.Hankealue
import fi.hel.haitaton.hanke.domain.alkuPvm
import fi.hel.haitaton.hanke.domain.geometriat
import fi.hel.haitaton.hanke.domain.haittaAjanKestoDays
import fi.hel.haitaton.hanke.domain.kaistaHaitat
import fi.hel.haitaton.hanke.domain.kaistaPituusHaitat
import fi.hel.haitaton.hanke.domain.loppuPvm
import fi.hel.haitaton.hanke.geometria.Geometriat
import fi.hel.haitaton.hanke.roundToOneDecimal
import kotlin.math.max
import org.springframework.beans.factory.annotation.Autowired

open class TormaystarkasteluLaskentaService(
    @Autowired private val tormaysService: TormaystarkasteluTormaysService
) {

    fun calculateTormaystarkastelu(alueet: List<Hankealue>): TormaystarkasteluTulos? {
        if (!hasAllRequiredInformation(alueet)) {
            return null
        }

        val perusIndeksi = calculatePerusIndeksi(alueet)

        val pyorailyLuokittelu = pyorailyLuokittelu(alueet.geometriat())
        val pyorailyIndeksi = if (pyorailyLuokittelu >= 4) 3.0f else 1.0f

        val bussiLuokittelu = bussiLuokittelu(alueet.geometriat())
        val bussiIndeksi = if (bussiLuokittelu >= 3) 4.0f else 1.0f

        val raitiovaunuLuokittelu = raitiovaunuLuokittelu(alueet.geometriat())
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

    private fun calculatePerusIndeksi(alueet: List<Hankealue>): Float {
        val luokittelu = mutableMapOf<LuokitteluType, Int>()

        luokittelu[LuokitteluType.HAITTA_AJAN_KESTO] =
            RajaArvoLuokittelija.getHaittaAjanKestoLuokka(alueet.haittaAjanKestoDays()!!)
        luokittelu[LuokitteluType.TODENNAKOINEN_HAITTA_PAAAJORATOJEN_KAISTAJARJESTELYIHIN] =
            alueet.kaistaHaitat().maxOfOrNull { it.value }!!
        luokittelu[LuokitteluType.KAISTAJARJESTELYN_PITUUS] =
            alueet.kaistaPituusHaitat().maxOfOrNull { it.value }!!

        val katuluokkaLuokittelu = katuluokkaLuokittelu(alueet.geometriat())
        luokittelu[LuokitteluType.KATULUOKKA] = katuluokkaLuokittelu
        luokittelu[LuokitteluType.LIIKENNEMAARA] =
            liikennemaaraLuokittelu(alueet.geometriat(), katuluokkaLuokittelu)

        return calculatePerusIndeksiFromLuokittelu(luokittelu)
    }

    internal fun katuluokkaLuokittelu(geometriat: List<Geometriat>): Int {
        return if (tormaysService.anyIntersectsYleinenKatuosa(geometriat)) {
            // ON ylre_parts => street_classes?
            tormaysService.maxIntersectingLiikenteellinenKatuluokka(geometriat)
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
            tormaysService.maxIntersectingLiikenteellinenKatuluokka(geometriat)
            // JOS EI LÖYDY => Valitse ylre_classes
            ?: max
        }
    }

    internal fun liikennemaaraLuokittelu(
        geometriat: List<Geometriat>,
        katuluokkaLuokittelu: Int
    ): Int {
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
        val maxVolume = tormaysService.maxLiikennemaara(geometriat, radius) ?: 0
        return RajaArvoLuokittelija.getLiikennemaaraLuokka(maxVolume)
    }

    internal fun calculatePerusIndeksiFromLuokittelu(
        luokitteluByType: Map<LuokitteluType, Int>
    ): Float =
        perusIndeksiPainot
            .map { (type, weight) -> luokitteluByType[type]!! * weight }
            .sum()
            .roundToOneDecimal()

    internal fun pyorailyLuokittelu(geometriat: List<Geometriat>): Int =
        when {
            tormaysService.anyIntersectsWithCyclewaysPriority(geometriat) ->
                PyorailyTormaysLuokittelu.PRIORISOITU_REITTI
            tormaysService.anyIntersectsWithCyclewaysMain(geometriat) ->
                PyorailyTormaysLuokittelu.PAAREITTI
            else -> PyorailyTormaysLuokittelu.EI_PYORAILUREITTI
        }.value

    internal fun bussiLuokittelu(geometriat: List<Geometriat>): Int {
        if (tormaysService.anyIntersectsCriticalBusRoutes(geometriat)) {
            return BussiLiikenneLuokittelu.KAMPPI_RAUTATIENTORI.value
        }

        val bussireitit = tormaysService.getIntersectingBusRoutes(geometriat)

        val valueByRunkolinja =
            bussireitit.maxOfOrNull { it.runkolinja.toBussiLiikenneLuokittelu().value }
            // bussireitit is empty
            ?: return BussiLiikenneLuokittelu.EI_VAIKUTA.value

        val countOfRushHourBuses = bussireitit.sumOf { it.vuoromaaraRuuhkatunnissa }
        val valueByRajaArvo =
            RajaArvoLuokittelija.getBussiLiikenneRuuhkaLuokka(countOfRushHourBuses)

        return max(valueByRajaArvo, valueByRunkolinja)
    }

    private fun raitiovaunuLuokittelu(geometriat: List<Geometriat>) =
        tormaysService.maxIntersectingTramByLaneType(geometriat) ?: 0
}
