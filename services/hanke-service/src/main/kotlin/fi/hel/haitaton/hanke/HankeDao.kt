package fi.hel.haitaton.hanke

interface HankeDao {
    fun findHankeByHankeId(hankeId: String): OldHankeEntity?
    fun saveHankeGeometria(hankeEntity: OldHankeEntity, hankeGeometriat: HankeGeometriat)
    fun loadHankeGeometria(hanke: OldHankeEntity): HankeGeometriat?
    fun saveHanke(hanke: Hanke) : OldHankeEntity
}