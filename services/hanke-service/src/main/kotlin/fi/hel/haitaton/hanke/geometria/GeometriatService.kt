package fi.hel.haitaton.hanke.geometria

import fi.hel.haitaton.hanke.domain.Hanke

interface GeometriatService {
    /** Insert/Update geometries. */
    fun saveGeometriat(geometriat: Geometriat): Geometriat?

    /** Loads all Geometriat under Hanke. */
    fun loadGeometriat(hanke: Hanke): Geometriat?

    /** Loads single geometry object. */
    fun getGeometriat(geometriatId: Int): Geometriat?
}
