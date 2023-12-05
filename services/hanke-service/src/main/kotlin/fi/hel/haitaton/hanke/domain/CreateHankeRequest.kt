package fi.hel.haitaton.hanke.domain

import fi.hel.haitaton.hanke.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.Haitta123
import fi.hel.haitaton.hanke.TyomaaTyyppi
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.VaikutusAutoliikenteenKaistamaariin
import fi.hel.haitaton.hanke.Yhteyshenkilo
import io.swagger.v3.oas.annotations.media.Schema
import java.time.ZonedDateTime
import org.geojson.FeatureCollection

data class CreateHankeRequest(
    @field:Schema(
        description = "Name of the project, must not be blank.",
        maxLength = 100,
    )
    override val nimi: String,
    @field:Schema(
        description = "Shared Public Utility Site (Yhteinen kunnallistekninen työmaa). Optional.",
    )
    val onYKTHanke: Boolean? = null,
    @field:Schema(
        description =
            "Description of the project and the work done during it. Required for the project to be published.",
    )
    val kuvaus: String? = null,
    @field:Schema(
        description = "Current stage of the project. Required for the hanke to be published.",
    )
    override val vaihe: Vaihe? = null,
    @field:Schema(
        description =
            "Project owners, contact information. At least one is required for the hanke to be published.",
    )
    override val omistajat: List<NewYhteystieto>? = null,
    @field:Schema(
        description =
            "Property developers, contact information. Not required for the hanke to be published.",
    )
    override val rakennuttajat: List<NewYhteystieto>? = null,
    @field:Schema(
        description = "Executor of the work. Not required for the hanke to be published.",
    )
    override val toteuttajat: List<NewYhteystieto>? = null,
    @field:Schema(
        description = "Other contacts. Not required for the hanke to be published.",
    )
    override val muut: List<NewYhteystieto>? = null,
    @field:Schema(
        description = "Work site street address. Required for the hanke to be published.",
        maxLength = 2000,
    )
    override val tyomaaKatuosoite: String? = null,
    @field:Schema(
        description = "Work site types. Not required for the hanke to be published.",
    )
    val tyomaaTyyppi: Set<TyomaaTyyppi>? = null,
    @field:Schema(
        description =
            "Hanke areas data. At least one alue is required for the hanke to be published.",
    )
    override val alueet: List<NewHankealue>? = null,
) : BaseHanke

data class NewYhteystieto(
    @field:Schema(
        description = "Contact name. Full name if an actual person.",
    )
    override val nimi: String,
    @field:Schema(
        description = "Contact email address",
    )
    override val email: String,
    @field:Schema(
        description = "Sub-contacts, i.e. contacts of this contact",
    )
    override val alikontaktit: List<Yhteyshenkilo> = emptyList(),
    @field:Schema(
        description = "Phone number",
    )
    override val puhelinnumero: String?,
    @field:Schema(
        description = "Organisation name",
    )
    override val organisaatioNimi: String?,
    @field:Schema(
        description = "Contact department",
    )
    override val osasto: String?,
    @field:Schema(
        description = "Role of the contact",
    )
    override val rooli: String?,
    @field:Schema(
        description = "Contact type",
    )
    override val tyyppi: YhteystietoTyyppi? = null,
    @field:Schema(
        description = "Business id, for contacts with tyyppi other than YKSITYISHENKILO",
    )
    override val ytunnus: String? = null,
) : Yhteystieto {
    override val id by lazy { null }
}

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
    override val meluHaitta: Haitta123? = null,
    @field:Schema(
        description = "Dust nuisance",
    )
    override val polyHaitta: Haitta123? = null,
    @field:Schema(
        description = "Vibration nuisance",
    )
    override val tarinaHaitta: Haitta123? = null,
    @field:Schema(
        description = "Area name, must not be null or empty",
    )
    override val nimi: String,
) : Hankealue

data class NewGeometriat(
    @field:Schema(description = "The geometry data")
    override val featureCollection: FeatureCollection? = null,
) : HasFeatures
