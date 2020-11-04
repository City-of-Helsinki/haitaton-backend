package fi.hel.haitaton.hanke

import org.geojson.FeatureCollection

class HankeDaoImpl : HankeDao {
    override fun findHankeByHankeId(hankeId: String): HankeEntity? {
        // TODO proper implementation
        return if (hankeId == "INVALID") null else HankeEntity()
    }

    override fun saveHankeGeometria(hankeEntity: HankeEntity, hankeGeometria: FeatureCollection) {
        // TODO
    }
}
