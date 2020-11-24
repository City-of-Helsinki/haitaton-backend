package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke

interface HankeDao {
    fun findHankeByHankeId(hankeId: String): OldHankeEntity?
    fun saveHankeGeometria(hankeEntity: OldHankeEntity, hankeGeometriat: HankeGeometriat)
    fun loadHankeGeometria(hanke: OldHankeEntity): HankeGeometriat?
    fun saveHanke(hanke: Hanke) : OldHankeEntity
}