package fi.hel.haitaton.hanke.domain

import fi.hel.haitaton.hanke.ContactType
import io.swagger.v3.oas.annotations.media.Schema

data class ModifyHankeRequest(
    @field:Schema(
        description = "Shared Public Utility Site (Yhteinen kunnallistekninen työmaa). Optional.",
    )
    val onYKTHanke: Boolean?,
    @field:Schema(
        description = "Name of the project, must not be blank.",
    )
    override val nimi: String,
    @field:Schema(
        description =
            "Description of the project and the work done during it. Required for the project to be published.",
    )
    var kuvaus: String?,
    @field:Schema(
        description = "Current stage of the project. Required for the hanke to be published.",
    )
    override var vaihe: Hankevaihe?,
    @field:Schema(
        description =
            "Project owners, contact information. At least one is required for the hanke to be published.",
    )
    override val omistajat: List<ModifyHankeYhteystietoRequest> = listOf(),
    @field:Schema(
        description =
            "Property developers, contact information. Not required for the hanke to be published.",
    )
    override val rakennuttajat: List<ModifyHankeYhteystietoRequest>?,
    @field:Schema(
        description = "Executor of the work. Not required for the hanke to be published.",
    )
    override val toteuttajat: List<ModifyHankeYhteystietoRequest>?,
    @field:Schema(
        description = "Other contacts. Not required for the hanke to be published.",
    )
    override val muut: List<ModifyHankeYhteystietoRequest>?,
    @field:Schema(
        description = "Work site street address. Required for the hanke to be published.",
        maxLength = 2000,
    )
    override val tyomaaKatuosoite: String?,
    @field:Schema(
        description = "Work site types. Not required for the hanke to be published.",
    )
    val tyomaaTyyppi: Set<TyomaaTyyppi>,
    override val alueet: List<ModifyHankealueRequest>,
) : HankeRequest {
    override fun yhteystiedotByType(): Map<ContactType, List<ModifyHankeYhteystietoRequest>> =
        mapOf(
                ContactType.OMISTAJA to omistajat,
                ContactType.RAKENNUTTAJA to rakennuttajat,
                ContactType.TOTEUTTAJA to toteuttajat,
                ContactType.MUU to muut,
            )
            .mapValues { (_, yhteystiedot) -> yhteystiedot ?: listOf() }
}
