package fi.hel.haitaton.hanke.tormaystarkastelu

/**
 * Street class.
 * In case of TONTTIKATU_TAI_AJOYHTEYS value can be either 1 or 2 depending on other parameters.
 */
enum class TormaystarkasteluKatuluokka(
    internal val value: Int,
    internal val katuluokka: String,
    internal val dependedValue: Int = value
) {
    TONTTIKATU_TAI_AJOYHTEYS(1, "Tonttikatu tai ajoyhteys", 2),
    PAIKALLINEN_KOKOOJAKATU(3, "Paikallinen kokoojakatu"),
    ALUEELLINEN_KOKOOJAKATU(4, "Alueellinen kokoojakatu"),
    PAAKATU_TAI_MOOTTORIVAYLA(5, "Pääkatu tai moottoriväylä");

    companion object {
        fun valueOfKatuluokka(katuluokka: String): TormaystarkasteluKatuluokka? {
            return values().find { it.katuluokka == katuluokka }
        }
    }
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

/**
 * Bus route trunk line category
 */
enum class TormaystarkasteluBussiRunkolinja(internal val runkolinja: String) {
    EI("no"),
    LAHES("almost"),
    ON("yes");

    companion object {
        fun valueOfRunkolinja(runkolinja: String): TormaystarkasteluBussiRunkolinja? {
            return values().find { it.runkolinja == runkolinja }
        }
    }
}

/**
 * Cycling route category
 */
enum class TormaystarkasteluPyorailyreittiluokka(internal val value: Int, private val pyorailyvayla: String = "") {
    PRIORISOITU_REITTI(5, "priority"),
    PAAREITTI(4, "main"),
    EI_PYORAILYREITTI(0);

    companion object {
        fun valueOfPyorailyvayla(pyorailyvayla: String): TormaystarkasteluPyorailyreittiluokka? {
            return values().find { it.pyorailyvayla == pyorailyvayla }
        }
    }
}

class IllegalKatuluokkaException(katuluokka: String) :
    RuntimeException("Illegal ylre_class value in data set: $katuluokka")