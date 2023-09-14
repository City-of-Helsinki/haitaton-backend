package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HankeIds
import fi.hel.haitaton.hanke.domain.Hanke

object HankeIdsFactory {
    fun create(
        id: Int = HankeFactory.defaultId,
        hankeTunnus: String = HankeFactory.defaultHankeTunnus
    ): HankeIds = TestHankeIds(id, hankeTunnus)
}

data class TestHankeIds(override val id: Int, override val hankeTunnus: String) : HankeIds

fun Hanke.ids(): HankeIds = TestHankeIds(id!!, hankeTunnus!!)
