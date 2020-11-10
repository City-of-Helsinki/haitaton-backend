package fi.hel.haitaton.hanke

import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service


@Component
interface HankeService {

    /**
     * Fetch hanke with hankeId
     */
    fun loadHanke(hankeId: String): Hanke?

    /**
     * Save hanke.
     *
     * If hankeId missing that is resolved here and returned back within hanke
     */
    fun save(hanke: Hanke): Hanke?

}
