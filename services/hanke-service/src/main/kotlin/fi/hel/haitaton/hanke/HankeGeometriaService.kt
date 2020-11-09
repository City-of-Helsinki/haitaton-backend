package fi.hel.haitaton.hanke

interface HankeGeometriaService {
    fun saveGeometria(hankeId: String, hankeGeometriat: HankeGeometriat)
    fun loadGeometria(hankeId: String): HankeGeometriat?
}