package fi.hel.haitaton.hanke.geometria

import org.geojson.GeoJsonObject

interface GeometriatDao {
    fun createGeometriat(geometriat: Geometriat): Geometriat

    fun retrieveGeometriat(id: Int): Geometriat?

    /** Updates geometry rows by FIRST DELETING ALL OF THEM AND THEN CREATING NEW ROWS */
    fun updateGeometriat(geometriat: Geometriat)

    /** Deletes geometry rows BUT DOES NOT DELETE THE MASTER ROW (Geometriat row) */
    fun deleteGeometriat(geometriat: Geometriat)

    /**
     * Uses PostGIS to check if the geometries are valid.
     *
     * For e.g. polygons it checks - among other things - that the first and last coordinate are
     * identical and that the line doesn't intersect itself.
     *
     * Note that some empty geometries are valid, like empty GeometryCollections and
     * FeatureCollections.
     *
     * @return List of indexes in the feature collection where the validation failed.
     */
    fun validateGeometria(geometria: GeoJsonObject): InvalidDetail?

    fun validateGeometriat(geometriat: List<GeoJsonObject>): InvalidDetail?

    data class InvalidDetail(val reason: String, val location: String)

    fun calculateArea(geometria: GeoJsonObject): Float?
}
