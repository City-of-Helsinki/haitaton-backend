package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.HankealueEntity
import fi.hel.haitaton.hanke.roundToOneDecimal
import kotlin.math.max
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class TormaystarkasteluLaskentaService(
    @Autowired private val tormaysService: TormaystarkasteluTormaysService
) {

    fun calculateTormaystarkastelu(alue: HankealueEntity): TormaystarkasteluTulos? {
        if (!hasAllRequiredInformation(alue)) {
            return null
        }

        val autoliikenneindeksi = calculateAutoliikenneindeksi(alue)
        val pyoraliikenneindeksi = calculatePyoraliikenneindeksi(alue.geometriat!!)
        val linjaautoliikenneindeksi = calculateLinjaautoliikenneindeksi(alue.geometriat!!)
        val raitioliikenneindeksi = calculateRaitioliikenneindeksi(alue.geometriat!!)

        return TormaystarkasteluTulos(
            autoliikenneindeksi,
            pyoraliikenneindeksi,
            linjaautoliikenneindeksi,
            raitioliikenneindeksi,
        )
    }

    private fun hasAllRequiredInformation(alue: HankealueEntity): Boolean {
        return (alue.haittaAlkuPvm != null &&
            alue.haittaLoppuPvm != null &&
            alue.kaistaHaitta != null &&
            alue.kaistaPituusHaitta != null &&
            alue.geometriat != null)
    }

    private fun calculateAutoliikenneindeksi(alue: HankealueEntity): Float {
        val luokittelu = mutableMapOf<LuokitteluType, Int>()

        luokittelu[LuokitteluType.HAITTA_AJAN_KESTO] =
            RajaArvoLuokittelija.haittaajankestoluokka(alue.haittaAjanKestoDays()!!)
        luokittelu[LuokitteluType.VAIKUTUS_AUTOLIIKENTEEN_KAISTAMAARIIN] =
            alue.kaistaHaitta?.value!!
        luokittelu[LuokitteluType.AUTOLIIKENTEEN_KAISTAVAIKUTUSTEN_PITUUS] =
            alue.kaistaPituusHaitta?.value!!

        val katuluokkaLuokittelu = katuluokkaluokittelu(setOf(alue.geometriat!!))
        luokittelu[LuokitteluType.KATULUOKKA] = katuluokkaLuokittelu
        luokittelu[LuokitteluType.AUTOLIIKENTEEN_MAARA] =
            liikennemaaraluokittelu(setOf(alue.geometriat!!), katuluokkaLuokittelu)

        return calculateAutoliikenneindeksiFromLuokittelu(luokittelu)
    }

    internal fun katuluokkaluokittelu(geometriaIds: Set<Int>): Int {
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

    internal fun liikennemaaraluokittelu(geometriaIds: Set<Int>, katuluokkaluokittelu: Int): Int {
        if (katuluokkaluokittelu == 0) {
            return 0
        }
        // type of street (=street class) decides which volume data we use for traffic (buffering of
        // street width varies)
        val radius =
            if (katuluokkaluokittelu >= 4) {
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_30
            } else {
                TormaystarkasteluLiikennemaaranEtaisyys.RADIUS_15
            }
        val maxVolume = tormaysService.maxLiikennemaara(geometriaIds, radius) ?: 0
        return RajaArvoLuokittelija.liikennemaaraluokka(maxVolume)
    }

    internal fun calculateAutoliikenneindeksiFromLuokittelu(
        luokitteluByType: Map<LuokitteluType, Int>
    ): Float =
        autoliikenneindeksipainot
            .map { (type, weight) -> luokitteluByType[type]?.times(weight) ?: 0f }
            .sum()
            .roundToOneDecimal()

    internal fun calculatePyoraliikenneindeksi(geometriaId: Int): Float =
        tormaysService.maxIntersectingPyoraliikenneHierarkia(setOf(geometriaId))?.toFloat() ?: 0f

    internal fun calculateLinjaautoliikenneindeksi(geometriaId: Int): Float =
        linjaautoliikenneluokittelu(setOf(geometriaId)).toFloat()

    internal fun linjaautoliikenneluokittelu(geometriaIds: Set<Int>): Int {
        if (tormaysService.anyIntersectsCriticalBusRoutes(geometriaIds)) {
            return Linjaautoliikenneluokittelu.TARKEIMMAT_JOUKKOLIIKENNEKADUT.value
        }

        val bussireitit = tormaysService.getIntersectingBusRoutes(geometriaIds)

        val valueByRunkolinja =
            bussireitit.maxOfOrNull { it.runkolinja.toLinjaautoliikenneluokittelu().value }
                // bussireitit is empty
                ?: return Linjaautoliikenneluokittelu.EI_VAIKUTA_LINJAAUTOLIIKENTEESEEN.value

        val countOfRushHourBuses = bussireitit.sumOf { it.vuoromaaraRuuhkatunnissa }
        val valueByRajaArvo =
            RajaArvoLuokittelija.linjaautoliikenteenRuuhkavuoroluokka(countOfRushHourBuses)

        return max(valueByRajaArvo, valueByRunkolinja)
    }

    internal fun calculateRaitioliikenneindeksi(geometriaId: Int): Float =
        raitioliikenneluokittelu(setOf(geometriaId)).toFloat()

    internal fun raitioliikenneluokittelu(geometriaIds: Set<Int>): Int =
        when {
            tormaysService.anyIntersectsWithTramLines(geometriaIds) ->
                Raitioliikenneluokittelu.RAITIOTIEVERKON_RATAOSA_JOLLA_SAANNOLLISTA_LINJALIIKENNETTA
            tormaysService.anyIntersectsWithTramInfra(geometriaIds) ->
                Raitioliikenneluokittelu
                    .RAITIOTIEVERKON_RATAOSA_JOLLA_EI_SAANNOLLISTA_LINJALIIKENNETTA
            else -> Raitioliikenneluokittelu.EI_TUNNISTETTUJA_RAITIOTIEKISKOJA
        }.value

    companion object {
        val autoliikenneindeksipainot =
            mapOf(
                Pair(LuokitteluType.HAITTA_AJAN_KESTO, 0.1f),
                Pair(LuokitteluType.VAIKUTUS_AUTOLIIKENTEEN_KAISTAMAARIIN, 0.25f),
                Pair(LuokitteluType.AUTOLIIKENTEEN_KAISTAVAIKUTUSTEN_PITUUS, 0.2f),
                Pair(LuokitteluType.KATULUOKKA, 0.2f),
                Pair(LuokitteluType.AUTOLIIKENTEEN_MAARA, 0.25f)
            )
    }
}
