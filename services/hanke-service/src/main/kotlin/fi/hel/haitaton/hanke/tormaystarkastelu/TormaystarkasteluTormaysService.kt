package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.geometria.HankeGeometriat

interface TormaystarkasteluTormaysService {

    // yleinen katuosa, ylre_parts
    fun anyIntersectsYleinenKatuosa(hankegeometriat: List<HankeGeometriat>): Boolean

    // yleinen katualue, ylre_classes
    fun maxIntersectingYleinenkatualueKatuluokka(hankegeometriat: List<HankeGeometriat>): Int?

    // liikenteellinen katuluokka, street_classes
    fun maxIntersectingLiikenteellinenKatuluokka(hankegeometriat: List<HankeGeometriat>): Int?

    // kantakaupunki, central_business_area
    fun anyIntersectsWithKantakaupunki(hankegeometriat: List<HankeGeometriat>): Boolean

    fun maxLiikennemaara(
        hankegeometriat: List<HankeGeometriat>,
        etaisyys: TormaystarkasteluLiikennemaaranEtaisyys
    ): Int?

    fun anyIntersectsCriticalBusRoutes(hankegeometriat: List<HankeGeometriat>): Boolean

    fun getIntersectingBusRoutes(
        hankegeometriat: List<HankeGeometriat>
    ): Set<TormaystarkasteluBussireitti>

    fun maxIntersectingTramByLaneType(hankegeometriat: List<HankeGeometriat>): Int?

    fun anyIntersectsWithCyclewaysPriority(hankegeometriat: List<HankeGeometriat>): Boolean

    fun anyIntersectsWithCyclewaysMain(hankegeometriat: List<HankeGeometriat>): Boolean
}

/** There are two(2) separate traffic counts - one for radius of 15m and other for 30m */
enum class TormaystarkasteluLiikennemaaranEtaisyys(internal val radius: Int) {
    RADIUS_15(15),
    RADIUS_30(30)
}

/** Bus route */
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
