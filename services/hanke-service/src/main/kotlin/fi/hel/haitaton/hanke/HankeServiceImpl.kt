package fi.hel.haitaton.hanke

import mu.KotlinLogging
import org.geojson.FeatureCollection

private val logger = KotlinLogging.logger { }

class HankeServiceImpl(private val dao: HankeDao): HankeService {
    override fun saveGeometria(hankeId: String, hankeGeometria: FeatureCollection) {
        logger.info {
            "Saving Geometria for Hanke $hankeId: ${hankeGeometria.toJsonString()}"
        }
        val hanke = dao.findHankeByHankeId(hankeId) ?: throw HankeNotFoundException(hankeId)
        // TODO convert GeoJSON object to something else?
        dao.saveHankeGeometria(hanke, hankeGeometria)
        logger.info {
            "Saved Geometria for Hanke $hankeId"
        }
    }
}