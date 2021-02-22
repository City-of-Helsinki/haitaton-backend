package fi.hel.haitaton.hanke.geometria

import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.domain.Hanke
import mu.KotlinLogging
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
        // TODO: if the new geometry is empty, is it actually a removal?
        val hasGeom = isGeometryNonEmpty(hankeGeometriat)
        // Set/update the state flag in hanke data and save it
        hanke.tilat.onGeometrioita = hasGeom
        hankeService.updateHankeStateFlags(hanke)

        return if (oldHankeGeometriat == null) {
            hankeGeometriat.createdAt = now
            hankeGeometriat.version = 0
            hankeGeometriat.hankeId = hanke.id
            hankeGeometriat.modifiedAt = now
            hankeGeometriaDao.createHankeGeometriat(hankeGeometriat)
            logger.debug {
                "Created new geometries for Hanke $hankeTunnus"
            }
            hankeGeometriat
        } else {
            if (oldHankeGeometriat.version == null) {
                error("There is an old HankeGeometriat for Hanke ${hanke.hankeTunnus} but it has no 'version'")
            } else {
                oldHankeGeometriat.version = oldHankeGeometriat.version!! + 1
            }
            oldHankeGeometriat.modifiedAt = now
            oldHankeGeometriat.featureCollection = hankeGeometriat.featureCollection
            hankeGeometriaDao.updateHankeGeometriat(oldHankeGeometriat)
            logger.debug {
                "Updated geometries for Hanke $hankeTunnus"
            }
            oldHankeGeometriat
        }
    }

    override fun loadGeometriat(hankeTunnus: String): HankeGeometriat? {
        val hanke = hankeService.loadHanke(hankeTunnus) ?: throw HankeNotFoundException(hankeTunnus)
        val hankeGeometriat = hankeGeometriaDao.retrieveHankeGeometriat(hanke.id!!)
        hankeGeometriat?.includeHankeProperties(hanke)
        return hankeGeometriat
    }

    override fun loadGeometriat(hanke: Hanke): HankeGeometriat? {
        val hankeGeometriat = hankeGeometriaDao.retrieveHankeGeometriat(hanke.id!!)
        hankeGeometriat?.includeHankeProperties(hanke)
        return hankeGeometriat
    }

    private fun isGeometryNonEmpty(hankeGeometriat: HankeGeometriat): Boolean {
        // TODO: might check deeper, and if so, there could be some multi-thread unsafe things hidden (though mostly theoretical)...
        if (hankeGeometriat.featureCollection?.features.isNullOrEmpty()) return false
        return true
    }
}
