package fi.hel.haitaton.hanke.allu

data class CustomerWithContacts(val customer: Customer, val contacts: List<Contact>)

data class Contact(
        val name: String?,
        val postalAddress: PostalAddress?,
        val email: String?,
        val phone: String?,
        val orderer: Boolean = false
)

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

data class PostalAddress(val streetAddress: StreetAddress, val postalCode: String, val city: String)

data class StreetAddress(val streetName: String)

enum class CustomerType { PERSON, COMPANY, ASSOCIATION, OTHER }

data class AttachmentInfo(val id: Int, val mimeType: String, val name: String, val description: String)
