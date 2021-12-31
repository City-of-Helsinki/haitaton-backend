package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeSearch
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos

interface HankeService {

    /**
     * Fetch hanke with hankeTunnus.
     * Returns null if there is no hanke with the given tunnus.
     */
    fun loadHanke(hankeTunnus: String): Hanke?

    fun createHanke(hanke: Hanke): Hanke

    fun updateHanke(hanke: Hanke): Hanke

    fun loadAllHanke(hankeSearch: HankeSearch? = null): List<Hanke>

    fun loadHankkeetByIds(ids: List<Int>): List<Hanke>

}
