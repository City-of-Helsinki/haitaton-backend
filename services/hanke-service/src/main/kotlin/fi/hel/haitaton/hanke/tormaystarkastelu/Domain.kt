package fi.hel.haitaton.hanke.tormaystarkastelu

/**
 * Whether the geometry is located on "yleinen katualue" (ylre_parts)
 */
data class YleinenKatualueTormaystarkastelu(
    val katualue: Boolean,
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
 * Cycling route categories
 */
enum class TormaystarkasteluPyorailyreittiluokitus(private val value: Int, private val cycleway: String = "") {
    PRIORISOITU_REITTI(5, "priority"),
    PAAREITTI(4, "main"),
    EI_PYORAILYREITTI(0);

    companion object {
        fun valueOfCycleway(cycleway: String): TormaystarkasteluPyorailyreittiluokitus? {
            return values().find { it.cycleway == cycleway }
        }
    }
}
