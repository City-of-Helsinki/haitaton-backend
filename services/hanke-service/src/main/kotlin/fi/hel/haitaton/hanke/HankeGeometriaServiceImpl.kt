package fi.hel.haitaton.hanke

import mu.KotlinLogging

private val logger = KotlinLogging.logger { }

/*
TODO merge this into HankeService
 */
class HankeGeometriaServiceImpl(private val dao: HankeDao) : HankeGeometriaService {
    override fun saveGeometria(hankeId: String, hankeGeometriat: HankeGeometriat) {
        logger.info {
            "Saving Geometria for Hanke $hankeId: ${hankeGeometriat.toJsonString()}"
        }
        val hanke = dao.findHankeByHankeId(hankeId) ?: throw HankeNotFoundException(hankeId)
        // TODO convert GeoJSON object to something else?
        dao.saveHankeGeometria(hanke, hankeGeometriat)
        logger.info {
            "Saved Geometria for Hanke $hankeId"
        }
    }

    override fun loadGeometria(hankeId: String): HankeGeometriat? {
        val hanke = dao.findHankeByHankeId(hankeId) ?: throw HankeNotFoundException(hankeId)
        return dao.loadHankeGeometria(hanke)
    }
}