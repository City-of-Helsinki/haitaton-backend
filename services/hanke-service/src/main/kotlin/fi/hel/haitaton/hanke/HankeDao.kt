package fi.hel.haitaton.hanke

interface HankeDao {
    fun findHankeByHankeId(hankeId: String): HankeEntity?
    fun saveHankeGeometria(hankeEntity: HankeEntity, hankeGeometriat: HankeGeometriat)
    fun loadHankeGeometria(hanke: HankeEntity): HankeGeometriat?
}