package fi.hel.haitaton.hanke.domain

import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.tormaystarkastelu.Meluhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Polyhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Tarinahaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.VaikutusAutoliikenteenKaistamaariin
import io.swagger.v3.oas.annotations.media.Schema
import java.time.ZonedDateTime
import org.geojson.FeatureCollection

data class ModifyHankealueRequest(
    @field:Schema(
        description = "Id, set if this hankealue is not a new one.",
    )
    override var id: Int? = null,
    @field:Schema(
        description = "Area name, must not be null or empty",
    )
    override val nimi: String,
    @field:Schema(
        description = "Nuisance start date",
        maximum = "2099-12-31T23:59:59.99Z",
    )
    override val haittaAlkuPvm: ZonedDateTime?,
    @field:Schema(
        description = "Nuisance end date, must not be before haittaAlkuPvm",
        maximum = "2099-12-31T23:59:59.99Z",
    )
    override val haittaLoppuPvm: ZonedDateTime?,
    override val geometriat: ModifyGeometriaRequest?,
    @field:Schema(
        description = "Street lane hindrance value and explanation",
    )
    override val kaistaHaitta: VaikutusAutoliikenteenKaistamaariin?,
    @field:Schema(
        description = "Street lane hindrance length",
    )
    override val kaistaPituusHaitta: AutoliikenteenKaistavaikutustenPituus?,
    @field:Schema(
        description = "Noise nuisance",
    )
    override val meluHaitta: Meluhaitta?,
    @field:Schema(
        description = "Dust nuisance",
    )
    override val polyHaitta: Polyhaitta?,
    @field:Schema(
        description = "Vibration nuisance",
    )
    override val tarinaHaitta: Tarinahaitta?,
    @field:Schema(
        description = "Nuisance management plan for this area",
    )
    override val haittojenhallintasuunnitelma: Map<Haittojenhallintatyyppi, String>?
) : HasId<Int?>, Hankealue

data class ModifyGeometriaRequest(
    override val id: Int?,
    override val featureCollection: FeatureCollection?
) : HasId<Int?>, HasFeatures
