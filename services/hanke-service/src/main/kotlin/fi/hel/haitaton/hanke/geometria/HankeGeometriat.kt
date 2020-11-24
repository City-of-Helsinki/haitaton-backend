package fi.hel.haitaton.hanke.geometria

import org.geojson.FeatureCollection
import java.time.ZonedDateTime

data class HankeGeometriat(
        var hankeId: Int? = null,
        var featureCollection: FeatureCollection? = null,
        var version: Int? = null,
        var createdAt: ZonedDateTime? = null,
        var createdByUserId: String? = null,
        var updatedAt: ZonedDateTime? = null,
        var updatedByUserId: String? = null
)
