package fi.hel.haitaton.hanke.hakemus

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView
import fi.hel.haitaton.hanke.allu.Customer as AlluCustomer
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.allu.PostalAddress as AlluPostalAddress
import fi.hel.haitaton.hanke.allu.StreetAddress as AlluStreetAddress

const val DEFAULT_COUNTRY = "FI"

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonView(ChangeLogView::class)
data class InvoicingCustomer(
    val type: CustomerType?, // Mandatory in Allu, but not in drafts.
    val name: String,
    val postalAddress: PostalAddress?,
    val email: String?,
    val phone: String?,
    val registryKey: String?, // y-tunnus
    val ovt: String?, // e-invoice identifier (ovt-tunnus)
    val invoicingOperator: String?, // e-invoicing operator code
) {
    /**
     * Check if this customer contains any actual personal information.
     *
     * Country alone isn't considered personal information when it's dissociated from other
     * information, so it's not checked here.
     */
    fun hasPersonalInformation() =
        !(name.isBlank() &&
            email.isNullOrBlank() &&
            phone.isNullOrBlank() &&
            registryKey.isNullOrBlank() &&
            ovt.isNullOrBlank() &&
            invoicingOperator.isNullOrBlank())

    fun toAlluData(path: String): AlluCustomer =
        AlluCustomer(
            type ?: throw AlluDataException("$path.type", AlluDataError.NULL),
            name,
            postalAddress?.toAlluData(),
            email,
            phone,
            registryKey,
            ovt,
            invoicingOperator,
            DEFAULT_COUNTRY,
            null,
        )
}

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonView(ChangeLogView::class)
data class PostalAddress(
    val streetAddress: StreetAddress,
    val postalCode: String,
    val city: String,
) {
    fun toAlluData(): AlluPostalAddress =
        AlluPostalAddress(streetAddress.toAlluData(), postalCode, city)

    @JsonIgnore
    fun isBlank(): Boolean = streetAddress.isBlank() && postalCode.isBlank() && city.isBlank()
}

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonView(ChangeLogView::class)
data class StreetAddress(val streetName: String?) {
    fun toAlluData(): AlluStreetAddress = AlluStreetAddress(streetName)

    @JsonIgnore fun isBlank(): Boolean = streetName.isNullOrBlank()
}

fun PostalAddress?.isNullOrBlank(): Boolean = this == null || this.isBlank()
