package fi.hel.haitaton.hanke.tormaystarkastelu

import io.swagger.v3.oas.annotations.media.Schema
import java.time.ZonedDateTime
import org.geojson.FeatureCollection

data class TormaystarkasteluRequest(
    @field:Schema(
        description = "Geometry data",
    )
    val geometriat: Geometriat,
    @field:Schema(
        description = "Nuisance start date",
        maximum = "2099-12-31T23:59:59.99Z",
    )
    val haittaAlkuPvm: ZonedDateTime,
    @field:Schema(
        description = "Nuisance end date, must not be before haittaAlkuPvm",
        maximum = "2099-12-31T23:59:59.99Z",
    )
    val haittaLoppuPvm: ZonedDateTime,
    @field:Schema(
        description = "Street lane hindrance value",
    )
    val kaistaHaitta: VaikutusAutoliikenteenKaistamaariin,
    @field:Schema(
        description = "Street lane hindrance length",
    )
    val kaistaPituusHaitta: AutoliikenteenKaistavaikutustenPituus,
) {
    data class Geometriat(
        @field:Schema(description = "The geometry data") val featureCollection: FeatureCollection
    )
}
