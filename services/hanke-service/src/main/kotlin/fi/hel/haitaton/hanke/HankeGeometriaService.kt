package fi.hel.haitaton.hanke

interface HankeGeometriaService {
    /**
     * Creates a new or updates an existing HankeGeometriat
     */
    fun saveGeometria(hankeId: String, hankeGeometriat: HankeGeometriat)
    fun loadGeometria(hankeId: String): HankeGeometriat?
}