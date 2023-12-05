package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.domain.Hankealue
import fi.hel.haitaton.hanke.domain.alkuPvm
import fi.hel.haitaton.hanke.domain.autoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.domain.geometriat
import fi.hel.haitaton.hanke.domain.haittaAjanKestoDays
import fi.hel.haitaton.hanke.domain.loppuPvm
import fi.hel.haitaton.hanke.domain.vaikutusAutoliikenteenKaistamaariin
import fi.hel.haitaton.hanke.roundToOneDecimal
import kotlin.math.max
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class TormaystarkasteluLaskentaService(
    @Autowired private val tormaysService: TormaystarkasteluTormaysService
) {

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

    fun calculateTormaystarkastelu(
        alueet: List<Hankealue>,
        geometriaIds: Set<Int>
    ): TormaystarkasteluTulos? {
        if (!hasAllRequiredInformation(alueet)) {
            return null
        }

        val autoliikenneindeksi = calculateAutoliikenneindeksi(alueet, geometriaIds)
        val pyoraliikenneindeksi = calculatePyoraliikenneindeksi(geometriaIds)
        val linjaautoliikenneindeksi = calculateLinjaautoliikenneindeksi(geometriaIds)
        val raitioliikenneindeksi = calculateRaitioliikenneindeksi(geometriaIds)

        return TormaystarkasteluTulos(
            autoliikenneindeksi,
            pyoraliikenneindeksi,
            linjaautoliikenneindeksi,
            raitioliikenneindeksi,
        )
    }

    private fun hasAllRequiredInformation(alueet: List<Hankealue>): Boolean {
        return (alueet.alkuPvm() != null &&
            alueet.loppuPvm() != null &&
            alueet.vaikutusAutoliikenteenKaistamaariin().isNotEmpty() &&
            alueet.autoliikenteenKaistavaikutustenPituus().isNotEmpty() &&
            alueet.geometriat().isNotEmpty())
    }

    private fun calculateAutoliikenneindeksi(
        alueet: List<Hankealue>,
        geometriaIds: Set<Int>
    ): Float {
        val luokittelu = mutableMapOf<LuokitteluType, Int>()

        luokittelu[LuokitteluType.HAITTA_AJAN_KESTO] =
            RajaArvoLuokittelija.haittaajankestoluokka(alueet.haittaAjanKestoDays()!!)
        luokittelu[LuokitteluType.VAIKUTUS_AUTOLIIKENTEEN_KAISTAMAARIIN] =
            alueet.vaikutusAutoliikenteenKaistamaariin().maxOfOrNull { it.value }!!
        luokittelu[LuokitteluType.AUTOLIIKENTEEN_KAISTAVAIKUTUSTEN_PITUUS] =
            alueet.autoliikenteenKaistavaikutustenPituus().maxOfOrNull { it.value }!!

        val katuluokkaLuokittelu = katuluokkaluokittelu(geometriaIds)
        luokittelu[LuokitteluType.KATULUOKKA] = katuluokkaLuokittelu
        luokittelu[LuokitteluType.AUTOLIIKENTEEN_MAARA] =
            liikennemaaraluokittelu(geometriaIds, katuluokkaLuokittelu)

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

    internal fun calculatePyoraliikenneindeksi(geometriaIds: Set<Int>): Float {
        val pyoraliikenneluokittelu = pyoraliikenneluokittelu(geometriaIds)
        return if (pyoraliikenneluokittelu >= 4) 3.0f else 1.0f
    }

    internal fun pyoraliikenneluokittelu(geometriaIds: Set<Int>): Int =
        when {
            tormaysService.anyIntersectsWithCyclewaysPriority(geometriaIds) ->
                Pyoraliikenneluokittelu.PRIORISOITU_REITTI
            tormaysService.anyIntersectsWithCyclewaysMain(geometriaIds) ->
                Pyoraliikenneluokittelu.PAAREITTI
            else -> Pyoraliikenneluokittelu.EI_PYORAILUREITTI
        }.value

    internal fun calculateLinjaautoliikenneindeksi(geometriaIds: Set<Int>): Float =
        if (linjaautoliikenneluokittelu(geometriaIds) >= 3) 4.0f else 1.0f

    internal fun linjaautoliikenneluokittelu(geometriaIds: Set<Int>): Int {
        if (tormaysService.anyIntersectsCriticalBusRoutes(geometriaIds)) {
            return Linjaautoliikenneluokittelu.KAMPPI_RAUTATIENTORI.value
        }

        val bussireitit = tormaysService.getIntersectingBusRoutes(geometriaIds)

        val valueByRunkolinja =
            bussireitit.maxOfOrNull { it.runkolinja.toLinjaautoliikenneluokittelu().value }
                // bussireitit is empty
                ?: return Linjaautoliikenneluokittelu.EI_VAIKUTA.value

        val countOfRushHourBuses = bussireitit.sumOf { it.vuoromaaraRuuhkatunnissa }
        val valueByRajaArvo =
            RajaArvoLuokittelija.linjaautoliikenteenRuuhkavuoroluokka(countOfRushHourBuses)

        return max(valueByRajaArvo, valueByRunkolinja)
    }

    internal fun calculateRaitioliikenneindeksi(geometriaIds: Set<Int>): Float =
        raitioliikenneluokittelu(geometriaIds).toFloat()

    internal fun raitioliikenneluokittelu(geometriaIds: Set<Int>): Int =
        when {
            tormaysService.anyIntersectsWithTramLines(geometriaIds) ->
                Raitioliikenneluokittelu.RAITIOTIELINJA
            tormaysService.anyIntersectsWithTramInfra(geometriaIds) ->
                Raitioliikenneluokittelu.RAITIOTIEVERKON_RATAOSA
            else -> Raitioliikenneluokittelu.EI_RAITIOTIETA
        }.value
}
