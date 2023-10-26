package fi.hel.haitaton.hanke.geometria

import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView
import fi.hel.haitaton.hanke.NotInChangeLogView
import fi.hel.haitaton.hanke.domain.HasId
import io.swagger.v3.oas.annotations.media.Schema
import java.time.ZonedDateTime
import org.geojson.FeatureCollection

@Schema(description = "Geometry data")
data class Geometriat(
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Id, set by the service")
    override var id: Int? = null,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "The geometry data")
    var featureCollection: FeatureCollection? = null,
    //
    @field:Schema(description = "Version, set by the service")
    @JsonView(ChangeLogView::class)
    var version: Int? = null,
    //
    @field:Schema(description = "User id of the geometry data creator, set by the service")
    @JsonView(NotInChangeLogView::class)
    var createdByUserId: String? = null,
    //
    @field:Schema(description = "Timestamp of last modification, set by the service")
    @JsonView(NotInChangeLogView::class)
    var createdAt: ZonedDateTime? = null,
    //
    @field:Schema(description = "User id of the last modifier, set by the service")
    @JsonView(NotInChangeLogView::class)
    var modifiedByUserId: String? = null,
    //
    @field:Schema(description = "Timestamp of last modification, set by the service")
    @JsonView(NotInChangeLogView::class)
    var modifiedAt: ZonedDateTime? = null
) : HasId<Int> {
    fun withFeatureCollection(input: FeatureCollection): Geometriat {
        this.featureCollection = input
        return this
    }

    fun resetFeatureProperties(hankeTunnus: String?) {
        featureCollection?.let { collection ->
            collection.features.forEach { feature ->
                feature.properties = mutableMapOf<String, Any?>("hankeTunnus" to hankeTunnus)
            }
        }
    }

    @JsonView(NotInChangeLogView::class)
    fun hasFeatures(): Boolean {
        return !featureCollection?.features.isNullOrEmpty()
    }
}
