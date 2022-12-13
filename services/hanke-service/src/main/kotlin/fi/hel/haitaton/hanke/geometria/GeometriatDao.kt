package fi.hel.haitaton.hanke.geometria

interface GeometriatDao {
    fun createGeometriat(geometriat: Geometriat): Geometriat

    fun retrieveGeometriat(id: Int): Geometriat?

    /** Updates geometry rows by FIRST DELETING ALL OF THEM AND THEN CREATING NEW ROWS */
    fun updateGeometriat(geometriat: Geometriat)

    /** Deletes geometry rows BUT DOES NOT DELETE THE MASTER ROW (Geometriat row) */
    fun deleteGeometriat(geometriat: Geometriat)
}
