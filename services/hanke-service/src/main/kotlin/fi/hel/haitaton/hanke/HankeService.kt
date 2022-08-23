package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke

interface HankeService {

    /**
     * Fetch hanke with hankeTunnus.
     * Returns null if there is no hanke with the given tunnus.
     */
    fun loadHanke(hankeTunnus: String): Hanke?

    fun createHanke(hanke: Hanke): Hanke

    fun updateHanke(hanke: Hanke): Hanke

    fun deleteHanke(id: Int)

    fun loadAllHanke(): List<Hanke>

    fun loadHankkeetByIds(ids: List<Int>): List<Hanke>

}
