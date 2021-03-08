package fi.hel.haitaton.hanke.geometria

interface HankeGeometriatDao {
    fun createHankeGeometriat(hankeGeometriat: HankeGeometriat)
    fun retrieveHankeGeometriat(hankeId: Int): HankeGeometriat?

    /**
     * Updates geometry rows by FIRST DELETING ALL OF THEM AND THEN CREATING NEW ROWS
     */
    fun updateHankeGeometriat(hankeGeometriat: HankeGeometriat)

    /**
     * Deletes geometry rows BUT DOES NOT DELETE THE MASTER ROW (HankeGeometriat row)
     */
    fun deleteHankeGeometriat(hankeGeometriat: HankeGeometriat)
}
