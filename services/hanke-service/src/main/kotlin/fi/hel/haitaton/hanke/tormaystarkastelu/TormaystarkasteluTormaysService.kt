package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.geometria.HankeGeometriat

interface TormaystarkasteluTormaysService {

    // yleinen katuosa, ylre_parts
    fun anyIntersectsYleinenKatuosa(hankegeometriat: HankeGeometriat): Boolean

    // yleinen katualue, ylre_classes
    fun maxIntersectingYleinenkatualueKatuluokka(hankegeometriat: HankeGeometriat): Int?

    // liikenteellinen katuluokka, street_classes
    fun maxIntersectingLiikenteellinenKatuluokka(hankegeometriat: HankeGeometriat): Int?

    // kantakaupunki, central_business_area
    fun anyIntersectsWithKantakaupunki(hankegeometriat: HankeGeometriat): Boolean

    fun maxLiikennemaara(hankegeometriat: HankeGeometriat, etaisyys: TormaystarkasteluLiikennemaaranEtaisyys): Int?

    fun anyIntersectsCriticalBusRoutes(hankegeometriat: HankeGeometriat): Boolean

    fun getIntersectingBusRoutes(hankegeometriat: HankeGeometriat): Set<TormaystarkasteluBussireitti>

    fun maxIntersectingTramByLaneType(hankegeometriat: HankeGeometriat): Int?

    fun anyIntersectsWithCyclewaysPriority(hankegeometriat: HankeGeometriat): Boolean
    fun anyIntersectsWithCyclewaysMain(hankegeometriat: HankeGeometriat): Boolean

}

/**
 * There are two(2) separate traffic counts - one for radius of 15m and other for 30m
 */
enum class TormaystarkasteluLiikennemaaranEtaisyys(internal val radius: Int) {
    RADIUS_15(15),
    RADIUS_30(30);
}

/**
 * Bus route
 */
class TormaystarkasteluBussireitti(
        val reittiId: String,
        val suunta: Int,
        val vuoromaaraRuuhkatunnissa: Int,
        val runkolinja: TormaystarkasteluBussiRunkolinja
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TormaystarkasteluBussireitti) return false

        if (reittiId != other.reittiId) return false
        if (suunta != other.suunta) return false

        return true
    }

    override fun hashCode(): Int {
        var result = reittiId.hashCode()
        result = 31 * result + suunta
        return result
    }
}
