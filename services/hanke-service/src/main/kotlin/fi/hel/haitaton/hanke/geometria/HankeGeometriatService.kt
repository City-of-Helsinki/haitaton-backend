package fi.hel.haitaton.hanke.geometria

import fi.hel.haitaton.hanke.domain.Hanke

interface HankeGeometriatService {
    /** Insert/Update geometries. */
    fun saveGeometriat(geometriat: HankeGeometriat): HankeGeometriat?

    /** Loads all HankeGeometriat under Hanke. */
    fun loadGeometriat(hanke: Hanke): HankeGeometriat?

    /** Loads single geometry object. */
    fun getGeometriat(geometriatId: Int): HankeGeometriat?
}
