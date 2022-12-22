package fi.hel.haitaton.hanke.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView
import fi.hel.haitaton.hanke.NotInChangeLogView
import java.time.ZonedDateTime

// e.g. omistaja, arvioija, toteuttaja
data class HankeYhteystieto(
    @JsonView(ChangeLogView::class) override var id: Int?,

    // must have contact information fields:
    @JsonView(ChangeLogView::class) var sukunimi: String,
    @JsonView(ChangeLogView::class) var etunimi: String,
    @JsonView(ChangeLogView::class) var email: String,
    @JsonView(ChangeLogView::class) var puhelinnumero: String,

    // organisaatio (optional)
    @JsonView(ChangeLogView::class) var organisaatioId: Int?,
    @JsonView(ChangeLogView::class) var organisaatioNimi: String?,
    @JsonView(ChangeLogView::class) var osasto: String?,
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
        return sukunimi.isNotBlank() ||
            etunimi.isNotBlank() ||
            email.isNotBlank() ||
            puhelinnumero.isNotBlank()
    }

    /**
     * Returns true if all four mandatory fields are non-null, non-empty and non-whitespace-only.
     */
    @JsonIgnore
    fun isValid(): Boolean {
        return sukunimi.isNotBlank() &&
            etunimi.isNotBlank() &&
            email.isNotBlank() &&
            puhelinnumero.isNotBlank()
    }
}
