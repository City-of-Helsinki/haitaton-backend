package fi.hel.haitaton.hanke.domain

import fi.hel.haitaton.hanke.SuunnitteluVaihe
import fi.hel.haitaton.hanke.TyomaaTyyppi
import fi.hel.haitaton.hanke.Vaihe
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
    override val omistajat: List<HankeYhteystieto>? = null,
    @field:Schema(
        description =
            "Property developers, contact information. Not required for the hanke to be published.",
    )
    override val rakennuttajat: List<HankeYhteystieto>? = null,
    @field:Schema(
        description = "Executor of the work. Not required for the hanke to be published.",
    )
    override val toteuttajat: List<HankeYhteystieto>? = null,
    @field:Schema(
        description = "Other contacts. Not required for the hanke to be published.",
    )
    override val muut: List<HankeYhteystieto>? = null,
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
