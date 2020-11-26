package fi.hel.haitaton.hanke.geometria

interface HankeGeometriatDao {
    fun deleteHankeGeometriat(hankeId: Int)
    fun saveHankeGeometriat(hankeGeometriat: HankeGeometriat)
    fun loadHankeGeometriat(hankeId: Int): HankeGeometriat?
}
