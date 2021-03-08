package fi.hel.haitaton.hanke.geometria

import fi.hel.haitaton.hanke.domain.Hanke

interface HankeGeometriatService {
    /**
     * Creates a new or updates an existing HankeGeometriat
     */
    fun saveGeometriat(hankeTunnus: String, hankeGeometriat: HankeGeometriat): HankeGeometriat
    fun loadGeometriat(hankeTunnus: String): HankeGeometriat?
    fun loadGeometriat(hanke: Hanke): HankeGeometriat?
}
