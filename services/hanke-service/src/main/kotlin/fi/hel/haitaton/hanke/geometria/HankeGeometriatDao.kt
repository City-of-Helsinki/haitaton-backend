package fi.hel.haitaton.hanke.geometria

import fi.hel.haitaton.hanke.HankeEntity

interface HankeGeometriatDao {
    fun saveHankeGeometria(hankeEntity: HankeEntity, hankeGeometriat: HankeGeometriat)
    fun loadHankeGeometria(hankeEntity: HankeEntity): HankeGeometriat?
}
