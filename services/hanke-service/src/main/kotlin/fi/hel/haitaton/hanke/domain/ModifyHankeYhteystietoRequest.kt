package fi.hel.haitaton.hanke.domain

import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

data class ModifyHankeYhteystietoRequest(
    @JsonView(ChangeLogView::class)
    @field:Schema(
        description = "Id, set if this yhteystieto is not a new one.",
    )
    override val id: Int?,
    @field:Schema(
        description = "Contact name. Full name if an actual person.",
    )
    override var nimi: String,
    @field:Schema(
        description = "Contact email address",
    )
    override var email: String,
    @field:Schema(
        description = "Phone number",
    )
    override var puhelinnumero: String?,
    @field:Schema(
        description = "Organisation name",
    )
    override var organisaatioNimi: String?,
    @field:Schema(
        description = "Contact department",
    )
    override var osasto: String?,
    @field:Schema(
        description = "Role of the contact",
    )
    override var rooli: String?,
    @field:Schema(
        description = "Contact type",
    )
    override var tyyppi: YhteystietoTyyppi? = null,
    @field:Schema(
        description = "Business id, for contacts with tyyppi other than YKSITYISHENKILO",
    )
    override val ytunnus: String? = null,
    @field:Schema(
        description = "IDs of the hanke kayttajat to contact for this contact",
    )
    val yhteyshenkilot: List<UUID>,
) : Yhteystieto
