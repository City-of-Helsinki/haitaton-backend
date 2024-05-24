package fi.hel.haitaton.hanke.domain

import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.tormaystarkastelu.Meluhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Polyhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Tarinahaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.VaikutusAutoliikenteenKaistamaariin
import io.swagger.v3.oas.annotations.media.Schema
import java.time.ZonedDateTime
import org.geojson.FeatureCollection

data class CreateHankeRequest(
    @field:Schema(
        description = "Name of the project, must not be blank.",
        maxLength = 100,
    )
    val nimi: String,
    @field:Schema(
        description = "Required project founder details",
    )
    val perustaja: HankePerustaja
)

data class HankePerustaja(
    @field:Schema(
        description =
            "Email of the founding user. Users with sufficient access to the project can add the email address to project contacts and application forms. Haitaton uses it is used for sending notification emails.",
    )
    val sahkoposti: String,
    @field:Schema(
        description =
            "Phone number of the founding user. Users with sufficient access to the project can add the phone number to project contacts and application forms.",
    )
    val puhelinnumero: String,
)

data class NewHankealue(
    @field:Schema(
        description = "Nuisance start date, must not be null",
        maximum = "2099-12-31T23:59:59.99Z",
    )
    override val haittaAlkuPvm: ZonedDateTime? = null,
    @field:Schema(
        description = "Nuisance end date, must not be before haittaAlkuPvm",
        maximum = "2099-12-31T23:59:59.99Z",
    )
    override val haittaLoppuPvm: ZonedDateTime? = null,
    @field:Schema(
        description = "Geometry data",
    )
    override val geometriat: NewGeometriat? = null,
    @field:Schema(
        description = "Street lane hindrance value and explanation",
    )
    override val kaistaHaitta: VaikutusAutoliikenteenKaistamaariin? = null,
    @field:Schema(
        description = "Street lane hindrance length",
    )
    override val kaistaPituusHaitta: AutoliikenteenKaistavaikutustenPituus? = null,
    @field:Schema(
        description = "Noise nuisance",
    )
    override val meluHaitta: Meluhaitta? = null,
    @field:Schema(
        description = "Dust nuisance",
    )
    override val polyHaitta: Polyhaitta? = null,
    @field:Schema(
        description = "Vibration nuisance",
    )
    override val tarinaHaitta: Tarinahaitta? = null,
    @field:Schema(
        description = "Area name, must not be null or empty",
    )
    override val nimi: String,
    @field:Schema(
        description = "Nuisance control plans for this area",
    )
    override val haittojenhallintasuunnitelma: Map<Haittojenhallintatyyppi, String>? = null
) : Hankealue

data class NewGeometriat(
    @field:Schema(description = "The geometry data")
    override val featureCollection: FeatureCollection? = null,
) : HasFeatures
