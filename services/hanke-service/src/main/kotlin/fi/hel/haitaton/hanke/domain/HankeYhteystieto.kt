package fi.hel.haitaton.hanke.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.Alikontakti
import fi.hel.haitaton.hanke.ChangeLogView
import fi.hel.haitaton.hanke.NotInChangeLogView
import java.time.ZonedDateTime

enum class YhteystietoTyyppi {
    YKSITYISHENKILO,
    YRITYS,
    YHTEISO,
}

data class HankeYhteystieto(
    @JsonView(ChangeLogView::class) override var id: Int?,

    // Mandatory info (person or juridical person):
    @JsonView(ChangeLogView::class) var nimi: String,
    @JsonView(ChangeLogView::class) var email: String,
    @JsonView(ChangeLogView::class) var puhelinnumero: String,

    // Optional subcontacts (person)
    @JsonView(ChangeLogView::class) var alikontaktit: List<Alikontakti> = emptyList(),

    // Optional
    @JsonView(ChangeLogView::class) var organisaatioId: Int?,
    @JsonView(ChangeLogView::class) var organisaatioNimi: String?,
    @JsonView(ChangeLogView::class) var osasto: String?,
    @JsonView(ChangeLogView::class) var rooli: String?,
    @JsonView(ChangeLogView::class) var tyyppi: YhteystietoTyyppi? = null,

    // Metadata
    @JsonView(NotInChangeLogView::class) var createdBy: String? = null,
    @JsonView(NotInChangeLogView::class) var createdAt: ZonedDateTime? = null,
    @JsonView(NotInChangeLogView::class) var modifiedBy: String? = null,
    @JsonView(NotInChangeLogView::class) var modifiedAt: ZonedDateTime? = null
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
        return nimi.isNotBlank() || email.isNotBlank() || puhelinnumero.isNotBlank()
    }
}
