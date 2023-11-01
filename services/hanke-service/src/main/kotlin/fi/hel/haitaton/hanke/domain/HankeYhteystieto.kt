package fi.hel.haitaton.hanke.domain

import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView
import fi.hel.haitaton.hanke.NotInChangeLogView
import fi.hel.haitaton.hanke.Yhteyshenkilo
import io.swagger.v3.oas.annotations.media.Schema
import java.time.ZonedDateTime

enum class YhteystietoTyyppi {
    YKSITYISHENKILO,
    YRITYS,
    YHTEISO,
}

@Schema(description = "Hanke contact")
data class HankeYhteystieto(
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Id, set by the service")
    override var id: Int?,

    // Mandatory info (person or juridical person):
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Contact name. Full name if an actual person.")
    override var nimi: String,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Contact email address")
    override var email: String,

    // Optional subcontacts (person)
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Sub-contacts, i.e. contacts of this contact")
    override var alikontaktit: List<Yhteyshenkilo> = emptyList(),

    // Optional
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Phone number")
    override var puhelinnumero: String?,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Organisation name")
    override var organisaatioNimi: String?,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Contact department")
    override var osasto: String?,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Role of the contact")
    override var rooli: String?,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Contact type")
    override var tyyppi: YhteystietoTyyppi? = null,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Business id, for contacts with tyyppi other than YKSITYISHENKILO")
    override val ytunnus: String? = null,

    // Metadata
    @JsonView(NotInChangeLogView::class)
    @field:Schema(description = "User id of the creator, set by the service")
    var createdBy: String? = null,
    //
    @JsonView(NotInChangeLogView::class)
    @field:Schema(description = "Timestamp of creation, set by the service")
    var createdAt: ZonedDateTime? = null,
    //
    @JsonView(NotInChangeLogView::class)
    @field:Schema(description = "User id of the last modifier, set by the service")
    var modifiedBy: String? = null,
    //
    @JsonView(NotInChangeLogView::class)
    @field:Schema(description = "Timestamp of last modification, set by the service")
    var modifiedAt: ZonedDateTime? = null
) : HasId<Int?>, Yhteystieto
