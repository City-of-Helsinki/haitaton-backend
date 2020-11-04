package fi.hel.haitaton.hanke

import org.springframework.stereotype.Service


@Service
class HankeService {

    /**
     * Fetch hanke with hankeId
     */
    fun loadHanke(hankeId: String): Hanke? {
        return null //TODO: implementation
    }

    /**
     * Save hanke.
     *
     * If hankeId missing that is resolved here and returned back within hanke
     */
    fun save(hanke: Hanke): Hanke? {
        return null //TODO: implementation
    }

}
