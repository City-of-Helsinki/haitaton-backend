package fi.hel.haitaton.hanke.tormaystarkastelu

interface TormaystarkasteluTormaysService {

    // yleinen katuosa, ylre_parts
    fun anyIntersectsYleinenKatuosa(geometriaIds: Set<Int>): Boolean

    // yleinen katualue, ylre_classes
    fun maxIntersectingYleinenkatualueKatuluokka(geometriaIds: Set<Int>): Int?

    // liikenteellinen katuluokka, street_classes
    fun maxIntersectingLiikenteellinenKatuluokka(geometriaIds: Set<Int>): Int?

    // kantakaupunki, central_business_area
    fun anyIntersectsWithKantakaupunki(geometriaIds: Set<Int>): Boolean

    fun maxLiikennemaara(
        geometriaIds: Set<Int>,
        etaisyys: TormaystarkasteluLiikennemaaranEtaisyys
    ): Int?

    fun anyIntersectsCriticalBusRoutes(geometriaIds: Set<Int>): Boolean

    fun getIntersectingBusRoutes(geometriaIds: Set<Int>): Set<TormaystarkasteluBussireitti>

    fun maxIntersectingTramByLaneType(geometriaIds: Set<Int>): Int?

    fun anyIntersectsWithCyclewaysPriority(geometriaIds: Set<Int>): Boolean

    fun anyIntersectsWithCyclewaysMain(geometriaIds: Set<Int>): Boolean
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
