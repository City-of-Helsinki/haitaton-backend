package fi.hel.haitaton.hanke.application

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView
import fi.hel.haitaton.hanke.allu.Contact as AlluContact
import fi.hel.haitaton.hanke.allu.Customer as AlluCustomer
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.allu.CustomerWithContacts as AlluCustomerWithContacts
import fi.hel.haitaton.hanke.allu.PostalAddress as AlluPostalAddress
import fi.hel.haitaton.hanke.allu.StreetAddress as AlluStreetAddress

@JsonIgnoreProperties(ignoreUnknown = true)
data class CustomerWithContacts(val customer: Customer, val contacts: List<Contact>) {
    fun toAlluData(path: String): AlluCustomerWithContacts {
        return AlluCustomerWithContacts(
            customer.toAlluData("$path.customer"),
            contacts.map { it.toAlluData() }
        )
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class Contact(
    val name: String?,
    val postalAddress: PostalAddress?,
    val email: String?,
    val phone: String?,
    val orderer: Boolean = false,
) {
    /** Check if this contact is blank, i.e. it doesn't contain any actual contact information. */
    @JsonIgnore
    fun isBlank() =
        name.isNullOrBlank() &&
            email.isNullOrBlank() &&
            phone.isNullOrBlank() &&
            postalAddress.isNullOrBlank()

    fun hasInformation() = !isBlank()

    fun toAlluData(): AlluContact =
        AlluContact(name, postalAddress?.toAlluData(), email, phone, orderer)
}

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonView(ChangeLogView::class)
data class Customer(
    val type: CustomerType?, // Mandatory in Allu, but not in drafts.
    val name: String,
    val country: String, // ISO 3166-1 alpha-2 country code
    val postalAddress: PostalAddress?,
    val email: String?,
    val phone: String?,
    val registryKey: String?, // ssn or y-tunnus
    val ovt: String?, // e-invoice identifier (ovt-tunnus)
    val invoicingOperator: String?, // e-invoicing operator code
    val sapCustomerNumber: String?, // customer's sap number
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

    fun toAlluData(path: String): AlluCustomer =
        AlluCustomer(
            type ?: throw AlluDataException("$path.type", AlluDataError.NULL),
            name,
            country,
            postalAddress?.toAlluData(),
            email,
            phone,
            registryKey,
            ovt,
            invoicingOperator,
            sapCustomerNumber,
        )
}

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonView(ChangeLogView::class)
data class PostalAddress(
    val streetAddress: StreetAddress,
    val postalCode: String,
    val city: String,
) {
    /** Check if this address is blank, i.e. none of fields have any information. */
    @JsonIgnore
    fun isBlank() =
        streetAddress.streetName.isNullOrBlank() && postalCode.isBlank() && city.isBlank()

    fun toAlluData(): AlluPostalAddress =
        AlluPostalAddress(streetAddress.toAlluData(), postalCode, city)
}

/** Check if this address is blank, i.e. none of fields have any information. */
fun PostalAddress?.isNullOrBlank() = this?.isBlank() ?: true

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonView(ChangeLogView::class)
data class StreetAddress(val streetName: String?) {
    fun toAlluData(): AlluStreetAddress = AlluStreetAddress(streetName)
}
