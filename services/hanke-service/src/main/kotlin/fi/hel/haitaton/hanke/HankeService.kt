package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke

interface HankeService {

    /**
     * Fetch hanke with hankeId
     */
    fun loadHanke(hankeId: String): Hanke?

    fun createHanke(hanke: Hanke): Hanke

    fun updateHanke(hanke: Hanke): Hanke



}
