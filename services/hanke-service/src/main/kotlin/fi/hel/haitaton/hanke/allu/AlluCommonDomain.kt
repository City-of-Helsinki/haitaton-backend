package fi.hel.haitaton.hanke.allu

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView

@JsonIgnoreProperties(ignoreUnknown = true)
data class CustomerWithContacts(val customer: Customer, val contacts: List<Contact>)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Contact(
    val name: String?,
    val postalAddress: PostalAddress?,
    val email: String?,
    val phone: String?,
    val orderer: Boolean = false
) {
    /** Check if this contact is blank, i.e. it doesn't contain any actual contact information. */
    @JsonIgnore
    fun isBlank() =
        name.isNullOrBlank() &&
            email.isNullOrBlank() &&
            phone.isNullOrBlank() &&
            postalAddress.isNullOrBlank()

    fun hasInformation() = !isBlank()
}

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonView(ChangeLogView::class)
data class Customer(
    val type: CustomerType,
    val name: String,
    val country: String, // ISO 3166-1 alpha-2 country code
    val postalAddress: PostalAddress?,
    val email: String?,
    val phone: String?,
    val registryKey: String?, // ssn or y-tunnus
    val ovt: String?, // e-invoice identifier (ovt-tunnus)
    val invoicingOperator: String?, // e-invoicing operator code
    val sapCustomerNumber: String? // customer's sap number
) {
    /** Check if this customer is blank, i.e. it doesn't contain any actual customer information. */
    @JsonIgnore
    fun isBlank() =
        name.isBlank() &&
            country.isBlank() &&
            postalAddress.isNullOrBlank() &&
            email.isNullOrBlank() &&
            phone.isNullOrBlank() &&
            registryKey.isNullOrBlank() &&
            ovt.isNullOrBlank() &&
            invoicingOperator.isNullOrBlank() &&
            sapCustomerNumber.isNullOrBlank()

    fun hasInformation() = !isBlank()
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class PostalAddress(
    val streetAddress: StreetAddress,
    val postalCode: String,
    val city: String
) {
    /** Check if this address is blank, i.e. none of fields have any information. */
    @JsonIgnore
    fun isBlank() =
        streetAddress.streetName.isNullOrBlank() && postalCode.isBlank() && city.isBlank()
}

/** Check if this address is blank, i.e. none of fields have any information. */
fun PostalAddress?.isNullOrBlank() = this?.isBlank() ?: true

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonView(ChangeLogView::class)
data class StreetAddress(val streetName: String?)

@JsonIgnoreProperties(ignoreUnknown = true)
enum class CustomerType {
    PERSON,
    COMPANY,
    ASSOCIATION,
    OTHER
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AttachmentInfo(
    val id: Int,
    val mimeType: String,
    val name: String,
    val description: String
)
