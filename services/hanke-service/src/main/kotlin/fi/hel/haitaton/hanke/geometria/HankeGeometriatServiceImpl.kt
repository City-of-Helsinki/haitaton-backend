package fi.hel.haitaton.hanke.geometria

import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.toJsonString
import mu.KotlinLogging
import java.time.ZonedDateTime
import javax.transaction.Transactional

private val logger = KotlinLogging.logger { }

@Transactional
open class HankeGeometriatServiceImpl(
        private val hankeRepository: HankeRepository,
        private val hankeGeometriaDao: HankeGeometriatDao) : HankeGeometriatService {
    override fun saveGeometriat(hankeTunnus: String, hankeGeometriat: HankeGeometriat): HankeGeometriat {
        logger.info {
            "Saving Geometria for Hanke $hankeTunnus: ${hankeGeometriat.toJsonString()}"
        }
        val hanke = hankeRepository.findByHankeTunnus(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)
        val now = ZonedDateTime.now(TZ_UTC)
        val oldHankeGeometriat = hankeGeometriaDao.loadHankeGeometriat(hanke.id!!)
        if (oldHankeGeometriat == null) {
            hankeGeometriat.createdAt = now
            hankeGeometriat.version = 1
        } else {
            hankeGeometriat.createdAt = oldHankeGeometriat.createdAt ?: oldHankeGeometriat.updatedAt ?: now
            if (oldHankeGeometriat.version == null) {
                error("There is an old HankeGeometriat for Hanke ${hanke.hankeTunnus} but it has no 'version'")
            } else {
                hankeGeometriat.version = oldHankeGeometriat.version!! + 1
            }
        }
        hankeGeometriat.hankeId = hanke.id
        hankeGeometriat.updatedAt = now
        hankeGeometriaDao.saveHankeGeometriat(hankeGeometriat)
        logger.info {
            "Saved Geometria for Hanke $hankeTunnus"
        }
        return hankeGeometriat
    }

    override fun loadGeometriat(hankeTunnus: String): HankeGeometriat? {
        val hanke = hankeRepository.findByHankeTunnus(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)
        return hankeGeometriaDao.loadHankeGeometriat(hanke.id!!)
    }
}