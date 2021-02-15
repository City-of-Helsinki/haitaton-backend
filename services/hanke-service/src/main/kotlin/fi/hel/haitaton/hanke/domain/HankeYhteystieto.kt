package fi.hel.haitaton.hanke.domain

import java.time.ZonedDateTime

// e.g. omistaja, arvioija, toteuttaja
data class HankeYhteystieto(
        var id: Int?,

        // must have contact information fields:
        var sukunimi: String,
        var etunimi: String,
        var email: String,
        var puhelinnumero: String,

        // organisaatio (optional)
        var organisaatioId: Int?,
        var organisaatioNimi: String?,
        var osasto: String?,

        var createdBy: String? = null,
        var createdAt: ZonedDateTime? = null,
        var modifiedBy: String? = null,
        var modifiedAt: ZonedDateTime? = null
) {

    /**
     * Returns true if at least one Yhteystieto-field is non-null, non-empty and non-whitespace-only.
     */
    fun isAnyFieldSet(): Boolean {
        return isAnyMandatoryFieldSet() || !organisaatioNimi.isNullOrBlank() || !osasto.isNullOrBlank()
    }

    /**
     * Returns true if at least one mandatory Yhteystieto-field is non-null, non-empty and non-whitespace-only.
     */
    fun isAnyMandatoryFieldSet(): Boolean {
        return sukunimi.isNotBlank() || etunimi.isNotBlank()
                || email.isNotBlank() || puhelinnumero.isNotBlank()
    }

    /**
     * Returns true if all four mandatory fields are non-null, non-empty and non-whitespace-only.
     */
    fun isValid(): Boolean {
        return sukunimi.isNotBlank() && etunimi.isNotBlank()
                && email.isNotBlank() && puhelinnumero.isNotBlank()
    }

}





