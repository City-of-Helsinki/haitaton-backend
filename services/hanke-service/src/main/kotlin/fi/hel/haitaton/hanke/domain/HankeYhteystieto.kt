package fi.hel.haitaton.hanke.domain

import com.fasterxml.jackson.annotation.JsonIgnore
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
    var nimi: String,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Contact email address")
    var email: String,

    // Optional subcontacts (person)
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Sub-contacts, i.e. contacts of this contact")
    var alikontaktit: List<Yhteyshenkilo> = emptyList(),

    // Optional
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Phone number")
    var puhelinnumero: String?,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Organisation name")
    var organisaatioNimi: String?,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Contact department")
    var osasto: String?,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Role of the contact")
    var rooli: String?,
    //
    @JsonView(ChangeLogView::class)
    @field:Schema(description = "Contact type")
    var tyyppi: YhteystietoTyyppi? = null,

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
) : HasId<Int> {

    /**
     * Returns true if at least one Yhteystieto-field is non-null, non-empty and
     * non-whitespace-only.
     */
    @JsonIgnore
    fun isAnyFieldSet(): Boolean {
        return isAnyMandatoryFieldSet() ||
            !organisaatioNimi.isNullOrBlank() ||
            !osasto.isNullOrBlank()
    }

    /**
     * Returns true if at least one mandatory Yhteystieto-field is non-null, non-empty and
     * non-whitespace-only.
     */
    @JsonIgnore
    fun isAnyMandatoryFieldSet(): Boolean {
        return nimi.isNotBlank() || email.isNotBlank()
    }
}
