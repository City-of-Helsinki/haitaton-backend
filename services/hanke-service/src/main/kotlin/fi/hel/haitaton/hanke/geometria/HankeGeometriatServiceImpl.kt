package fi.hel.haitaton.hanke.geometria

import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.domain.Hanke
import mu.KotlinLogging
import org.springframework.security.core.context.SecurityContextHolder
import java.time.ZonedDateTime
import javax.transaction.Transactional

private val logger = KotlinLogging.logger { }

open class HankeGeometriatServiceImpl(
    private val hankeService: HankeService,
    private val hankeGeometriaDao: HankeGeometriatDao
) : HankeGeometriatService {

    @Transactional
    override fun saveGeometriat(hankeTunnus: String, hankeGeometriat: HankeGeometriat): HankeGeometriat {
        val hanke = hankeService.loadHanke(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)
        val now = ZonedDateTime.now(TZ_UTC)
        val oldHankeGeometriat = hankeGeometriaDao.retrieveHankeGeometriat(hanke.id!!)
        val hasFeatures = hankeGeometriat.hasFeatures()
        if (oldHankeGeometriat == null && !hasFeatures) {
            throw IllegalArgumentException("New HankeGeometriat does not contain any Features")
        }
        // Set/update the state flag in hanke data and save it
        hanke.tilat.onGeometrioita = hasFeatures
        hankeService.updateHankeStateFlags(hanke)

        val username = SecurityContextHolder.getContext().authentication.name

        return when {
            !hasFeatures -> {
                // DELETE
                hankeGeometriat.id = oldHankeGeometriat!!.id
                hankeGeometriat.hankeId = oldHankeGeometriat.hankeId
                hankeGeometriat.createdByUserId = oldHankeGeometriat.createdByUserId
                hankeGeometriat.createdAt = oldHankeGeometriat.createdAt
                hankeGeometriat.modifiedByUserId = username
                hankeGeometriat.modifiedAt = now
                hankeGeometriat.version = oldHankeGeometriat.version!! + 1
                hankeGeometriaDao.deleteHankeGeometriat(hankeGeometriat)
                logger.info {
                    "Deleted geometries for Hanke $hankeTunnus"
                }
                hankeGeometriat
            }
            oldHankeGeometriat == null -> {
                // CREATE
                hankeGeometriat.createdByUserId = username
                hankeGeometriat.createdAt = now
                hankeGeometriat.version = 0
                hankeGeometriat.hankeId = hanke.id
                hankeGeometriaDao.createHankeGeometriat(hankeGeometriat)
                logger.info {
                    "Created new geometries for Hanke $hankeTunnus"
                }
                hankeGeometriat
            }
            else -> {
                // UPDATE
                if (oldHankeGeometriat.version == null) {
                    error("There is an old HankeGeometriat for Hanke ${hanke.hankeTunnus} but it has no 'version'")
                } else {
                    oldHankeGeometriat.version = oldHankeGeometriat.version!! + 1
                }
                oldHankeGeometriat.modifiedByUserId = username
                oldHankeGeometriat.modifiedAt = now
                oldHankeGeometriat.featureCollection = hankeGeometriat.featureCollection
                hankeGeometriaDao.updateHankeGeometriat(oldHankeGeometriat)
                logger.info {
                    "Updated geometries for Hanke $hankeTunnus"
                }
                oldHankeGeometriat
            }
        }
    }

    override fun loadGeometriat(hankeTunnus: String): HankeGeometriat? {
        val hanke = hankeService.loadHanke(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)
        val hankeGeometriat = hankeGeometriaDao.retrieveHankeGeometriat(hanke.id!!)
        hankeGeometriat?.includeHankeProperties(hanke)
        logger.info {
            "Loaded Hanke Geometria for $hankeTunnus: ${hankeGeometriat?.toLogString()}"
        }
        return hankeGeometriat
    }

    override fun loadGeometriat(hanke: Hanke): HankeGeometriat? {
        val hankeGeometriat = hankeGeometriaDao.retrieveHankeGeometriat(hanke.id!!)
        hankeGeometriat?.includeHankeProperties(hanke)
        return hankeGeometriat
    }
}
