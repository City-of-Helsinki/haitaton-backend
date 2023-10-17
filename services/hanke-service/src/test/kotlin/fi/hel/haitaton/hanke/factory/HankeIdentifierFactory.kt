package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HankeIdentifier
import fi.hel.haitaton.hanke.domain.SavedHanke

object HankeIdentifierFactory {
    fun create(
        id: Int = HankeFactory.defaultId,
        hankeTunnus: String = HankeFactory.defaultHankeTunnus
    ): HankeIdentifier = TestHankeIdentifier(id, hankeTunnus)
}

data class TestHankeIdentifier(override val id: Int, override val hankeTunnus: String) :
    HankeIdentifier

fun SavedHanke.identifier(): HankeIdentifier = TestHankeIdentifier(id, hankeTunnus)
