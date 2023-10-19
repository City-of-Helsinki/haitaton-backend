package fi.hel.haitaton.hanke.geometria

import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.domain.HasFeatures
import fi.hel.haitaton.hanke.domain.hasFeatures
import java.time.ZonedDateTime
import mu.KotlinLogging
import org.geojson.FeatureCollection
import org.springframework.transaction.annotation.Transactional

private val logger = KotlinLogging.logger {}

open class GeometriatServiceImpl(private val hankeGeometriaDao: GeometriatDao) : GeometriatService {

    @Transactional
    override fun saveGeometriat(geometriat: HasFeatures, existingId: Int?): Geometriat? {
        GeometriatValidator.expectValid(geometriat)

        val oldGeometriat = existingId?.let { hankeGeometriaDao.retrieveGeometriat(existingId) }
        val hasFeatures = geometriat.hasFeatures()

        return when {
            oldGeometriat == null && !hasFeatures ->
                throw IllegalArgumentException("New Geometriat does not contain any Features")
            oldGeometriat != null && !hasFeatures -> deleteGeometria(oldGeometriat)
            oldGeometriat == null -> createGeometria(geometriat)
            else -> updateGeometria(oldGeometriat, geometriat.featureCollection!!)
        }
    }

    override fun createGeometria(geometriat: HasFeatures): Geometriat {
        val created =
            Geometriat(
                    featureCollection = geometriat.featureCollection,
                    createdByUserId = currentUserId(),
                    createdAt = ZonedDateTime.now(TZ_UTC),
                    modifiedByUserId = null,
                    modifiedAt = null,
                    version = 0,
                )
                .let(hankeGeometriaDao::createGeometriat)

        logger.info { "Created new geometries ${created.id}" }
        return created
    }

    private fun deleteGeometria(geometriat: Geometriat): Geometriat? {
        hankeGeometriaDao.deleteGeometriat(geometriat)
        logger.info { "Deleted geometries ${geometriat.id}" }
        return null
    }

    private fun updateGeometria(
        geometriat: Geometriat,
        newFeatures: FeatureCollection
    ): Geometriat {
        if (geometriat.version == null) {
            error("There is an old Geometriat ${geometriat.id} but it has no 'version'")
        } else {
            geometriat.version = geometriat.version!! + 1
        }
        geometriat.modifiedByUserId = currentUserId()
        geometriat.modifiedAt = ZonedDateTime.now(TZ_UTC)
        geometriat.featureCollection = newFeatures
        hankeGeometriaDao.updateGeometriat(geometriat)
        logger.info { "Updated geometries ${geometriat.id}" }
        return geometriat
    }

    /** Get geometries by geometry object id. */
    override fun getGeometriat(geometriatId: Int): Geometriat? {
        return hankeGeometriaDao.retrieveGeometriat(geometriatId)
    }
}
