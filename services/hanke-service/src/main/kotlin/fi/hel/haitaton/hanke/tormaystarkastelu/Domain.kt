package fi.hel.haitaton.hanke.tormaystarkastelu

/**
 * Whether the geometry is located on "yleinen katualue" (ylre_parts)
 */
data class YleinenKatualueTormaystarkastelu(
    val katualue: Boolean,
    val hankegeometriaId: Int
)

data class YleinenKatuluokkaTormaystarkastelu(
    val katuluokka: YleinenKatuluokka,
    val hankegeometriaId: Int
)

/**
 * Category of the cycling route (0 = no cycling route) the geometry is located on
 */
data class PyorailyTormaystarkastelu(
    val reittiluokka: TormaystarkasteluPyorailyreittiluokitus,
    val hankegeometriaId: Int
)

/**
 * General street class.
 * In case of TONTTIKATU_TAI_AJOYHTEYS value can be either 1 or 2 depending on other parameters.
 */
enum class YleinenKatuluokka(private val value: Int, private val katuluokka: String, private val dependedValue: Int = value) {
    TONTTIKATU_TAI_AJOYHTEYS(1, "Tonttikatu tai ajoyhteys", 2),
    PAIKALLINEN_KOKOOJAKATU(3, "Paikallinen kokoojakatu"),
    ALUEELLINEN_KOKOOJAKATU(4, "Alueellinen kokoojakatu"),
    PAAKATU_TAI_MOOTTORIVAYLA(5, "Pääkatu tai moottoriväylä");

    companion object {
        fun valueOfKatuluokka(katuluokka: String): YleinenKatuluokka? {
            return values().find { it.katuluokka == katuluokka }
        }
    }
}

/**
 * Cycling route categories
 */
enum class TormaystarkasteluPyorailyreittiluokitus(private val value: Int, private val pyorailyvayla: String = "") {
    PRIORISOITU_REITTI(5, "priority"),
    PAAREITTI(4, "main"),
    EI_PYORAILYREITTI(0);

    companion object {
        fun valueOfPyorailyvayla(pyorailyvayla: String): TormaystarkasteluPyorailyreittiluokitus? {
            return values().find { it.pyorailyvayla == pyorailyvayla }
        }
    }
}

class IllegalKatuluokkaException(katuluokka: String): RuntimeException("Illegal ylre_class value in data set: $katuluokka")