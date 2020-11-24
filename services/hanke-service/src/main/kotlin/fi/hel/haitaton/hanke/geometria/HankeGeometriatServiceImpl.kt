package fi.hel.haitaton.hanke.geometria

import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.toJsonString
import mu.KotlinLogging
import java.time.ZonedDateTime

private val logger = KotlinLogging.logger { }

class HankeGeometriatServiceImpl(
        private val hankeRepository: HankeRepository,
        private val hankeGeometriaDao: HankeGeometriatDao) : HankeGeometriatService {
    override fun saveGeometriat(hankeTunnus: String, hankeGeometriat: HankeGeometriat): HankeGeometriat {
        logger.info {
            "Saving Geometria for Hanke $hankeTunnus: ${hankeGeometriat.toJsonString()}"
        }
        val hanke = hankeRepository.findByHankeTunnus(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)
        val now = ZonedDateTime.now(TZ_UTC)
        val oldHankeGeometriat = hankeGeometriaDao.loadHankeGeometria(hanke)
        if (oldHankeGeometriat == null) {
            hankeGeometriat.createdAt = now
            hankeGeometriat.version = 1
        } else {
            hankeGeometriat.createdAt = oldHankeGeometriat.createdAt
            if (oldHankeGeometriat.version == null) {
                error("There is an old HankeGeometriat for Hanke ${hanke.hankeTunnus} but it has no 'version'")
            } else {
                hankeGeometriat.version = oldHankeGeometriat.version!! + 1
            }
        }
        hankeGeometriat.hankeId = hanke.id
        hankeGeometriat.updatedAt = now
        // TODO convert GeoJSON object to something else?
        hankeGeometriaDao.saveHankeGeometria(hanke, hankeGeometriat)
        logger.info {
            "Saved Geometria for Hanke $hankeTunnus"
        }
        return hankeGeometriat
    }

    override fun loadGeometriat(hankeTunnus: String): HankeGeometriat? {
        val hanke = hankeRepository.findByHankeTunnus(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)
        return hankeGeometriaDao.loadHankeGeometria(hanke)
    }
}