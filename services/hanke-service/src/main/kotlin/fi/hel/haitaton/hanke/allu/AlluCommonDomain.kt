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
    val email: String?,
    val phone: String?,
    val orderer: Boolean = false
) {
    /** Check if this contact is blank, i.e. it doesn't contain any actual contact information. */
    @JsonIgnore fun isBlank() = listOf(name, email, phone).all { it.isNullOrBlank() }

    fun hasInformation() = !isBlank()
}

/** Non-null fields are not mandatory in Allu. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Customer(
    val type: CustomerType,
    val name: String,
    val postalAddress: PostalAddress?,
    val email: String?,
    val phone: String?,
    val registryKey: String?, // y-tunnus
    val ovt: String?, // e-invoice identifier (ovt-tunnus)
    val invoicingOperator: String?, // e-invoicing operator code
    val country: String, // ISO 3166-1 alpha-2 country code
    val sapCustomerNumber: String? // customer's sap number
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
            invoicingOperator.isNullOrBlank() &&
            sapCustomerNumber.isNullOrBlank())
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class PostalAddress(
    val streetAddress: StreetAddress,
    val postalCode: String,
    val city: String
)

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
data class AttachmentMetadata(
    val id: Int?,
    val mimeType: String,
    val name: String,
    val description: String?,
)

data class Comment(
    val commentator: String?,
    val commentContent: String,
)
