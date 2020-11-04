package fi.hel.haitaton.hanke

import org.geojson.FeatureCollection

interface HankeDao {
    fun findHankeByHankeId(hankeId: String): HankeEntity?
    fun saveHankeGeometria(hankeEntity: HankeEntity, hankeGeometria: FeatureCollection)
}