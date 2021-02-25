package fi.hel.haitaton.hanke.geometria

interface HankeGeometriatDao {
    fun createHankeGeometriat(hankeGeometriat: HankeGeometriat)
    fun retrieveHankeGeometriat(hankeId: Int): HankeGeometriat?
    fun updateHankeGeometriat(hankeGeometriat: HankeGeometriat)
    fun deleteHankeGeometriat(hankeGeometriat: HankeGeometriat)
}
