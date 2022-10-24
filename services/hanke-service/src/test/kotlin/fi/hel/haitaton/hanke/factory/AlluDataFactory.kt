package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.allu.Contact
import fi.hel.haitaton.hanke.allu.Customer
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.allu.PostalAddress
import fi.hel.haitaton.hanke.allu.StreetAddress

object AlluDataFactory {

    fun createPostalAddress(
        streetAddress: StreetAddress = StreetAddress("Katu 1"),
        postalCode: String = "00100",
        city: String = "Helsinki",
    ) = PostalAddress(streetAddress, postalCode, city)

    fun createPersonCustomer() =
        Customer(
            type = CustomerType.PERSON,
            name = "Teppo Testihenkilö",
            country = "FI",
            postalAddress = createPostalAddress(),
            email = "teppo@example.test",
            phone = "04012345678",
            registryKey = "281192-937W",
            ovt = null,
            invoicingOperator = null,
            sapCustomerNumber = null,
        )

    fun createCompanyCustomer() =
        Customer(
            type = CustomerType.COMPANY,
            name = "DNA",
            country = "FI",
            postalAddress = createPostalAddress(),
            email = "info@dna.test",
            phone = "+3581012345678",
            registryKey = "3766028-0",
            ovt = null,
            invoicingOperator = null,
            sapCustomerNumber = null,
        )

    fun createContact(
        name: String? = "Teppo Testihenkilö",
        postalAddress: PostalAddress? = createPostalAddress(),
        email: String? = "teppo@example.test",
        phone: String? = "04012345678",
    ) = Contact(name, postalAddress, email, phone)
}
