package fi.hel.haitaton.hanke.geometria

import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.domain.Hanke
import java.time.ZonedDateTime
import javax.transaction.Transactional
import mu.KotlinLogging
import org.springframework.security.core.context.SecurityContextHolder

private val logger = KotlinLogging.logger {}

open class HankeGeometriatServiceImpl(private val hankeGeometriaDao: HankeGeometriatDao) :
    HankeGeometriatService {

    @Transactional
    override fun saveGeometriat(geometriat: HankeGeometriat): HankeGeometriat {
        HankeGeometriatValidator.exceptValid(geometriat)

        val now = ZonedDateTime.now(TZ_UTC)
        val oldHankeGeometriat =
            if (geometriat.id != null) hankeGeometriaDao.retrieveGeometriat(geometriat.id!!)
            else null
        val hasFeatures = geometriat.hasFeatures()
        if (oldHankeGeometriat == null && !hasFeatures) {
            throw IllegalArgumentException("New HankeGeometriat does not contain any Features")
        }

        val username = SecurityContextHolder.getContext().authentication.name

        return when {
            !hasFeatures -> {
                // DELETE
                geometriat.id = oldHankeGeometriat!!.id
                geometriat.createdByUserId = oldHankeGeometriat.createdByUserId
                geometriat.createdAt = oldHankeGeometriat.createdAt
                geometriat.modifiedByUserId = username
                geometriat.modifiedAt = now
                geometriat.version = oldHankeGeometriat.version!! + 1
                hankeGeometriaDao.deleteHankeGeometriat(geometriat)
                logger.info { "Deleted geometries ${geometriat.id}" }
                geometriat
            }
            oldHankeGeometriat == null -> {
                // CREATE
                geometriat.createdByUserId = username
                geometriat.createdAt = now
                geometriat.version = 0
                val created =
                    hankeGeometriaDao.createHankeGeometriat(geometriat)
                        ?: throw IllegalArgumentException("New geometry missing fields")
                logger.info { "Created new geometries ${created.id}" }
                created
            }
            else -> {
                // UPDATE
                if (oldHankeGeometriat.version == null) {
                    error(
                        "There is an old HankeGeometriat ${oldHankeGeometriat.id} but it has no 'version'"
                    )
                } else {
                    oldHankeGeometriat.version = oldHankeGeometriat.version!! + 1
                }
                oldHankeGeometriat.modifiedByUserId = username
                oldHankeGeometriat.modifiedAt = now
                oldHankeGeometriat.featureCollection = geometriat.featureCollection
                hankeGeometriaDao.updateHankeGeometriat(oldHankeGeometriat)
                logger.info { "Updated geometries ${oldHankeGeometriat.id}" }
                oldHankeGeometriat
            }
        }
    }

    /**
     * Load geometries with object id.
     *
     * NOTE This method is deprecated. Create a new endpoint for querying geometry data.
     *
     * contain multiple return values
     */
    override fun loadGeometriat(hanke: Hanke): HankeGeometriat? {
        val hankeGeometriat = hankeGeometriaDao.retrieveGeometriat(hanke.id!!)
        hankeGeometriat?.includeHankeProperties(hanke)
        return hankeGeometriat
    }

    /** Get geometries by geometry object id. */
    override fun getGeometriat(geometriatId: Int): HankeGeometriat? {
        return hankeGeometriaDao.retrieveGeometriat(geometriatId)
    }
}
