package fi.hel.haitaton.hanke.allu

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView
import fi.hel.haitaton.hanke.domain.BusinessId

@JsonIgnoreProperties(ignoreUnknown = true)
data class CustomerWithContacts(val customer: Customer, val contacts: List<Contact>)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Contact(
    val name: String?,
    val email: String?,
    val phone: String?,
    val orderer: Boolean = false
)

/** Non-null fields are not mandatory in Allu. */
@JsonIgnoreProperties(ignoreUnknown = true)
data class Customer(
    val type: CustomerType,
    val name: String,
    val country: String, // ISO 3166-1 alpha-2 country code
    val email: String?,
    val phone: String?,
    val registryKey: BusinessId?, // y-tunnus
    val ovt: String?, // e-invoice identifier (ovt-tunnus)
    val invoicingOperator: String?, // e-invoicing operator code
    val sapCustomerNumber: String? // customer's sap number
)

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
