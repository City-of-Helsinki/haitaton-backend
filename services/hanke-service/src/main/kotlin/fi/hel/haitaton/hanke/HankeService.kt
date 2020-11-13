package fi.hel.haitaton.hanke

interface HankeService {

    /**
     * Fetch hanke with hankeId
     */
    fun loadHanke(hankeId: String): Hanke?

    fun createHanke(hanke: Hanke): Hanke

    fun updateHanke(hanke: Hanke): Hanke

}
