package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke
import org.springframework.transaction.annotation.Transactional

interface HankeService {

    /** Fetch hanke with hankeTunnus. Returns null if there is no hanke with the given tunnus. */
    fun loadHanke(hankeTunnus: String): Hanke?

    fun getHankeId(hankeTunnus: String): Int?

    @Transactional fun createHanke(hanke: Hanke): Hanke

    @Transactional fun updateHanke(hanke: Hanke): Hanke

    @Transactional fun deleteHanke(hankeId: Int, userId: String): Boolean

    fun loadAllHanke(): List<Hanke>

    fun loadPublicHanke(): List<Hanke>

    fun loadHankkeetByIds(ids: List<Int>): List<Hanke>

    fun loadHankkeetByUserId(userId: String): List<Hanke>
}
