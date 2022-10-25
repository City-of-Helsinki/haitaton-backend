package fi.hel.haitaton.hanke.allu

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.apache.commons.lang3.StringUtils

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
    fun isBlank() =
        StringUtils.isBlank(name) &&
            StringUtils.isBlank(email) &&
            StringUtils.isBlank(phone) &&
            postalAddress?.isBlank() ?: true
}

@JsonIgnoreProperties(ignoreUnknown = true)
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
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PostalAddress(
    val streetAddress: StreetAddress,
    val postalCode: String,
    val city: String
) {
    /** Check if this address is blank, i.e. none of fields have any information. */
    fun isBlank() =
        StringUtils.isBlank(streetAddress.streetName) &&
            StringUtils.isBlank(postalCode) &&
            StringUtils.isBlank(city)
}

@JsonIgnoreProperties(ignoreUnknown = true) data class StreetAddress(val streetName: String?)

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
