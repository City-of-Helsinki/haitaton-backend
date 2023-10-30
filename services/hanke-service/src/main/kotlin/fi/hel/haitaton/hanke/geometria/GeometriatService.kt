package fi.hel.haitaton.hanke.geometria

import fi.hel.haitaton.hanke.domain.HasFeatures

interface GeometriatService {
    /** Insert/Update geometries. */
    fun saveGeometriat(geometriat: HasFeatures, existingId: Int?): Geometriat?

    fun createGeometriat(geometriat: HasFeatures): Geometriat

    /** Loads single geometry object. */
    fun getGeometriat(geometriatId: Int): Geometriat?
}
