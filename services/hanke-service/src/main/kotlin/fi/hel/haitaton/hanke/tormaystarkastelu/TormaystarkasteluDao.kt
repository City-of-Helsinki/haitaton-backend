package fi.hel.haitaton.hanke.tormaystarkastelu

interface TormaystarkasteluDao {
    /**
     * Checks which "yleinen katualue" these Hanke geometries are on
     */
    fun yleisetKatualueet(hankegeometriatId: Int): List<YleinenKatualueTormaystarkastelu>

    /**
     * Checks whether these Hanke geometries are on any categorized cycling route
     */
    fun pyorailyreitit(hankegeometriatId: Int): List<PyorailyTormaystarkastelu>
    fun yleisetKatuluokat(hankegeometriatId: Int): List<YleinenKatuluokkaTormaystarkastelu>
}
