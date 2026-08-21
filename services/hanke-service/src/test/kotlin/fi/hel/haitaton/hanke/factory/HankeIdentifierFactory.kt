package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HankeIdentifier
import fi.hel.haitaton.hanke.domain.Hanke

object HankeIdentifierFactory {
    fun create(
        id: Int = HankeFactory.DEFAULT_ID,
        hankeTunnus: String = HankeFactory.DEFAULT_HANKE_TUNNUS,
    ): HankeIdentifier = TestHankeIdentifier(id, hankeTunnus)
}

data class TestHankeIdentifier(override val id: Int, override val hankeTunnus: String) :
    HankeIdentifier

fun Hanke.identifier(): HankeIdentifier = TestHankeIdentifier(id, hankeTunnus)
