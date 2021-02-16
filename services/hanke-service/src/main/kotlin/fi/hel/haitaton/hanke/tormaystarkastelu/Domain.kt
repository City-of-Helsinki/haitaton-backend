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