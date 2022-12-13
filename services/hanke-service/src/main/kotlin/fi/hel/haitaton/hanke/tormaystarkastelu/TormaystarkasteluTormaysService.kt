package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.geometria.Geometriat

interface TormaystarkasteluTormaysService {

    // yleinen katuosa, ylre_parts
    fun anyIntersectsYleinenKatuosa(geometriat: List<Geometriat>): Boolean

    // yleinen katualue, ylre_classes
    fun maxIntersectingYleinenkatualueKatuluokka(geometriat: List<Geometriat>): Int?

    // liikenteellinen katuluokka, street_classes
    fun maxIntersectingLiikenteellinenKatuluokka(geometriat: List<Geometriat>): Int?

    // kantakaupunki, central_business_area
    fun anyIntersectsWithKantakaupunki(geometriat: List<Geometriat>): Boolean

    fun maxLiikennemaara(
            geometriat: List<Geometriat>,
            etaisyys: TormaystarkasteluLiikennemaaranEtaisyys
    ): Int?

    fun anyIntersectsCriticalBusRoutes(geometriat: List<Geometriat>): Boolean

    fun getIntersectingBusRoutes(
        geometriat: List<Geometriat>
    ): Set<TormaystarkasteluBussireitti>

    fun maxIntersectingTramByLaneType(geometriat: List<Geometriat>): Int?

    fun anyIntersectsWithCyclewaysPriority(geometriat: List<Geometriat>): Boolean

    fun anyIntersectsWithCyclewaysMain(geometriat: List<Geometriat>): Boolean
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
