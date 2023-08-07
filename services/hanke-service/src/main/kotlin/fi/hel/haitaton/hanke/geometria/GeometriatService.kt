package fi.hel.haitaton.hanke.geometria

interface GeometriatService {
    /** Insert/Update geometries. */
    fun saveGeometriat(geometriat: Geometriat): Geometriat?

    /** Loads single geometry object. */
    fun getGeometriat(geometriatId: Int): Geometriat?
}
