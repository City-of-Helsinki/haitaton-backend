package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke

interface HankeService {

    /**
     * Fetch hanke with hankeTunnus.
     * Either returns the hanke instance, or throws exception.
     */
    fun loadHanke(hankeTunnus: String): Hanke

    fun createHanke(hanke: Hanke): Hanke

    fun updateHanke(hanke: Hanke): Hanke


}
