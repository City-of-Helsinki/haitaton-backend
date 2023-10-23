package fi.hel.haitaton.hanke.domain

import fi.hel.haitaton.hanke.TyomaaTyyppi
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.Yhteyshenkilo
import io.swagger.v3.oas.annotations.media.Schema

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
    override val alueet: List<Hankealue>? = null,
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
