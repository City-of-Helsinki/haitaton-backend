package fi.hel.haitaton.hanke.tormaystarkastelu

interface TormaystarkasteluDao {

    fun pyorailyreitit(hankegeometriatId: Int): List<PyorailyTormaystarkastelu>
}
