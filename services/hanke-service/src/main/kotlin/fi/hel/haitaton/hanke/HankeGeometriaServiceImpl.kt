package fi.hel.haitaton.hanke

import mu.KotlinLogging
import org.geojson.FeatureCollection

private val logger = KotlinLogging.logger { }

/*
TODO merge this into HankeService
 */
class HankeGeometriaServiceImpl(private val dao: HankeDao) : HankeGeometriaService {
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

    override fun loadGeometria(hankeId: String): FeatureCollection? {
        val hanke = dao.findHankeByHankeId(hankeId) ?: throw HankeNotFoundException(hankeId)
        return dao.loadHankeGeometria(hanke)
    }
}