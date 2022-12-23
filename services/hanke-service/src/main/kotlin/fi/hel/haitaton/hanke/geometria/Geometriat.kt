package fi.hel.haitaton.hanke.geometria

import fi.hel.haitaton.hanke.domain.Hanke
import java.time.ZonedDateTime
import org.geojson.FeatureCollection

data class Geometriat(
    var id: Int? = null,
    var featureCollection: FeatureCollection? = null,
    var version: Int? = null,
    var createdByUserId: String? = null,
    var createdAt: ZonedDateTime? = null,
    var modifiedByUserId: String? = null,
    var modifiedAt: ZonedDateTime? = null
) {
    fun withFeatureCollection(featureCollection: FeatureCollection): Geometriat {
        this.featureCollection = featureCollection
        return this
    }

    fun includeHankeProperties(hanke: Hanke) {
        this.featureCollection?.let { featureCollection ->
            featureCollection.features.forEach { feature ->
                if (feature.properties == null) {
                    feature.properties = mutableMapOf()
                }
                feature.properties["hankeTunnus"] = hanke.hankeTunnus
                // Add here other properties when needed
            }
        }
    }

    fun hasFeatures(): Boolean {
        return !featureCollection?.features.isNullOrEmpty()
    }
}
