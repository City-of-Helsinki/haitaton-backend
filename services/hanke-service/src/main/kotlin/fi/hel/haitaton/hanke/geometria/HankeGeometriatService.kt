package fi.hel.haitaton.hanke.geometria

interface HankeGeometriatService {
    /**
     * Creates a new or updates an existing HankeGeometriat
     */
    fun saveGeometriat(hankeTunnus: String, hankeGeometriat: HankeGeometriat): HankeGeometriat
    fun loadGeometriat(hankeTunnus: String): HankeGeometriat?
}