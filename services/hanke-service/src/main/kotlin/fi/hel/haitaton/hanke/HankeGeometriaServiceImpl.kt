package fi.hel.haitaton.hanke

import mu.KotlinLogging
import java.time.ZonedDateTime

private val logger = KotlinLogging.logger { }

/*
TODO merge this into HankeService?
 */
class HankeGeometriaServiceImpl(private val dao: HankeDao) : HankeGeometriaService {
    override fun saveGeometria(hankeId: String, hankeGeometriat: HankeGeometriat) {
        logger.info {
            "Saving Geometria for Hanke $hankeId: ${hankeGeometriat.toJsonString()}"
        }
        val hanke = dao.findHankeByHankeId(hankeId) ?: throw HankeNotFoundException(hankeId)
        val now = ZonedDateTime.now(TZ_UTC)
        val oldHankeGeometriat = dao.loadHankeGeometria(hanke)
        if (oldHankeGeometriat == null) {
            hankeGeometriat.createdAt = now
        } else {
            if (oldHankeGeometriat.version == null) {
                error("There is an old HankeGeometriat for Hanke ${hanke.id} but it has no 'version'")
            } else {
                hankeGeometriat.version = oldHankeGeometriat.version!! + 1
            }
        }
        hankeGeometriat.hankeId = hanke.id
        hankeGeometriat.updatedAt = now
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