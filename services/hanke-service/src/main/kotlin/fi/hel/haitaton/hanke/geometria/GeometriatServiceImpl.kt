package fi.hel.haitaton.hanke.geometria

import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.domain.Hanke
import java.time.ZonedDateTime
import javax.transaction.Transactional
import mu.KotlinLogging
import org.springframework.security.core.context.SecurityContextHolder

private val logger = KotlinLogging.logger {}

open class GeometriatServiceImpl(private val hankeGeometriaDao: GeometriatDao) :
    GeometriatService {

    @Transactional
    override fun saveGeometriat(geometriat: Geometriat): Geometriat {
        GeometriatValidator.expectValid(geometriat)

        val now = ZonedDateTime.now(TZ_UTC)
        val oldGeometriat =
            if (geometriat.id != null) hankeGeometriaDao.retrieveGeometriat(geometriat.id!!)
            else null
        val hasFeatures = geometriat.hasFeatures()
        if (oldGeometriat == null && !hasFeatures) {
            throw IllegalArgumentException("New Geometriat does not contain any Features")
        }

        val username = SecurityContextHolder.getContext().authentication.name

        return when {
            !hasFeatures -> {
                // DELETE
                geometriat.id = oldGeometriat!!.id
                geometriat.createdByUserId = oldGeometriat.createdByUserId
                geometriat.createdAt = oldGeometriat.createdAt
                geometriat.modifiedByUserId = username
                geometriat.modifiedAt = now
                geometriat.version = oldGeometriat.version!! + 1
                hankeGeometriaDao.deleteGeometriat(geometriat)
                logger.info { "Deleted geometries ${geometriat.id}" }
                geometriat
            }
            oldGeometriat == null -> {
                // CREATE
                geometriat.createdByUserId = username
                geometriat.createdAt = now
                geometriat.version = 0
                val created = hankeGeometriaDao.createGeometriat(geometriat)
                logger.info { "Created new geometries ${created.id}" }
                created
            }
            else -> {
                // UPDATE
                if (oldGeometriat.version == null) {
                    error(
                        "There is an old Geometriat ${oldGeometriat.id} but it has no 'version'"
                    )
                } else {
                    oldGeometriat.version = oldGeometriat.version!! + 1
                }
                oldGeometriat.modifiedByUserId = username
                oldGeometriat.modifiedAt = now
                oldGeometriat.featureCollection = geometriat.featureCollection
                hankeGeometriaDao.updateGeometriat(oldGeometriat)
                logger.info { "Updated geometries ${oldGeometriat.id}" }
                oldGeometriat
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
    override fun loadGeometriat(hanke: Hanke): Geometriat? {
        val geometriat = hankeGeometriaDao.retrieveGeometriat(hanke.id!!)
        geometriat?.includeHankeProperties(hanke)
        return geometriat
    }

    /** Get geometries by geometry object id. */
    override fun getGeometriat(geometriatId: Int): Geometriat? {
        return hankeGeometriaDao.retrieveGeometriat(geometriatId)
    }
}
