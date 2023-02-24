package fi.hel.haitaton.hanke.geometria

import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView
import fi.hel.haitaton.hanke.NotInChangeLogView
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HasId
import java.time.ZonedDateTime
import org.geojson.FeatureCollection

data class Geometriat(
    @JsonView(ChangeLogView::class) override var id: Int? = null,
    @JsonView(ChangeLogView::class) var featureCollection: FeatureCollection? = null,
    @JsonView(ChangeLogView::class) var version: Int? = null,
    @JsonView(NotInChangeLogView::class) var createdByUserId: String? = null,
    @JsonView(NotInChangeLogView::class) var createdAt: ZonedDateTime? = null,
    @JsonView(NotInChangeLogView::class) var modifiedByUserId: String? = null,
    @JsonView(NotInChangeLogView::class) var modifiedAt: ZonedDateTime? = null
) : HasId<Int> {
    fun withFeatureCollection(featureCollection: FeatureCollection): Geometriat {
        this.featureCollection = featureCollection
        return this
    }

    fun resetFeatureProperties(hanke: Hanke) {
        this.featureCollection?.let { featureCollection ->
            featureCollection.features.forEach { feature ->
                feature.properties = mutableMapOf<String, Any?>("hankeTunnus" to hanke.hankeTunnus)
            }
        }
    }

    @JsonView(NotInChangeLogView::class)
    fun hasFeatures(): Boolean {
        return !featureCollection?.features.isNullOrEmpty()
    }
}
