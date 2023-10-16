package fi.hel.haitaton.hanke.domain

import fi.hel.haitaton.hanke.ContactType
import fi.hel.haitaton.hanke.SuunnitteluVaihe
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
        description = "Current planning stage, must be defined if vaihe = SUUNNITTELU",
    )
    override val suunnitteluVaihe: SuunnitteluVaihe? = null,
    @field:Schema(
        description =
            "Project owners, contact information. At least one is required for the hanke to be published.",
    )
    val omistajat: List<Yhteystieto>? = null,
    @field:Schema(
        description =
            "Property developers, contact information. Not required for the hanke to be published.",
    )
    val rakennuttajat: List<Yhteystieto>? = null,
    @field:Schema(
        description = "Executor of the work. Not required for the hanke to be published.",
    )
    val toteuttajat: List<Yhteystieto>? = null,
    @field:Schema(
        description = "Other contacts. Not required for the hanke to be published.",
    )
    val muut: List<Yhteystieto>? = null,
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
) : BaseHanke {
    override fun extractYhteystiedot(): List<HankeYhteystieto> =
        listOfNotNull(omistajat, rakennuttajat, toteuttajat, muut).flatten().map {
            it.toHankeYhteystieto()
        }

    override fun yhteystiedotByType(): Map<ContactType, List<HankeYhteystieto>> {
        return mapOf(
                ContactType.OMISTAJA to omistajat,
                ContactType.RAKENNUTTAJA to rakennuttajat,
                ContactType.TOTEUTTAJA to toteuttajat,
                ContactType.MUU to muut,
            )
            .mapValues { (_, yhteystiedot) ->
                yhteystiedot?.map { it.toHankeYhteystieto() } ?: listOf()
            }
    }

    data class Yhteystieto(
        @field:Schema(
            description = "Contact name. Full name if an actual person.",
        )
        val nimi: String,
        @field:Schema(
            description = "Contact email address",
        )
        val email: String,
        @field:Schema(
            description = "Sub-contacts, i.e. contacts of this contact",
        )
        val alikontaktit: List<Yhteyshenkilo> = emptyList(),
        @field:Schema(
            description = "Phone number",
        )
        val puhelinnumero: String?,
        @field:Schema(
            description = "Organisation name",
        )
        val organisaatioNimi: String?,
        @field:Schema(
            description = "Contact department",
        )
        val osasto: String?,
        @field:Schema(
            description = "Role of the contact",
        )
        val rooli: String?,
        @field:Schema(
            description = "Contact type",
        )
        val tyyppi: YhteystietoTyyppi? = null,
        @field:Schema(
            description = "Business id, for contacts with tyyppi other than YKSITYISHENKILO",
        )
        val ytunnus: String? = null,
    ) {
        fun toHankeYhteystieto(): HankeYhteystieto =
            HankeYhteystieto(
                null,
                nimi,
                email,
                alikontaktit,
                puhelinnumero,
                organisaatioNimi,
                osasto,
                rooli,
                tyyppi,
                ytunnus
            )
    }
}
