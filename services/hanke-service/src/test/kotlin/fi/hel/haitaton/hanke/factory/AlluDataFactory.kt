package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.OBJECT_MAPPER
import fi.hel.haitaton.hanke.allu.ApplicationDto
import fi.hel.haitaton.hanke.allu.ApplicationType
import fi.hel.haitaton.hanke.allu.CableReportApplication
import fi.hel.haitaton.hanke.allu.Contact
import fi.hel.haitaton.hanke.allu.Customer
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.allu.CustomerWithContacts
import fi.hel.haitaton.hanke.allu.PostalAddress
import fi.hel.haitaton.hanke.allu.StreetAddress
import java.time.ZonedDateTime
import org.geojson.GeometryCollection

object AlluDataFactory {

    fun createPostalAddress(
        streetAddress: StreetAddress = StreetAddress("Katu 1"),
        postalCode: String = "00100",
        city: String = "Helsinki",
    ) = PostalAddress(streetAddress, postalCode, city)

    fun createPersonCustomer(
        type: CustomerType = CustomerType.PERSON,
        name: String = "Teppo Testihenkilö",
        country: String = "FI",
        postalAddress: PostalAddress? = createPostalAddress(),
        email: String? = "teppo@example.test",
        phone: String? = "04012345678",
        registryKey: String? = "281192-937W",
        ovt: String? = null,
        invoicingOperator: String? = null,
        sapCustomerNumber: String? = null,
    ) =
        Customer(
            type,
            name,
            country,
            postalAddress,
            email,
            phone,
            registryKey,
            ovt,
            invoicingOperator,
            sapCustomerNumber
        )

    fun createCompanyCustomer(
        type: CustomerType = CustomerType.COMPANY,
        name: String = "DNA",
        country: String = "FI",
        postalAddress: PostalAddress? = createPostalAddress(),
        email: String? = "info@dna.test",
        phone: String? = "+3581012345678",
        registryKey: String? = "3766028-0",
        ovt: String? = null,
        invoicingOperator: String? = null,
        sapCustomerNumber: String? = null,
    ) =
        Customer(
            type,
            name,
            country,
            postalAddress,
            email,
            phone,
            registryKey,
            ovt,
            invoicingOperator,
            sapCustomerNumber
        )

    fun createContact(
        name: String? = "Teppo Testihenkilö",
        postalAddress: PostalAddress? = createPostalAddress(),
        email: String? = "teppo@example.test",
        phone: String? = "04012345678",
    ) = Contact(name, postalAddress, email, phone)

    fun createCableReportApplication(
        name: String = "Johtoselvitys",
        customerWithContacts: CustomerWithContacts =
            CustomerWithContacts(createCompanyCustomer(), listOf(createContact())),
        geometry: GeometryCollection = GeometryCollection(),
        startTime: ZonedDateTime = DateFactory.getStartDatetime(),
        endTime: ZonedDateTime = DateFactory.getEndDatetime(),
        pendingOnClient: Boolean = false,
        identificationNumber: String = "identification",
        clientApplicationKind: String = "applicationKind",
        workDescription: String = "Work description.",
        contractorWithContacts: CustomerWithContacts =
            CustomerWithContacts(createCompanyCustomer(), listOf(createContact())),
    ) =
        CableReportApplication(
            name,
            customerWithContacts,
            geometry,
            startTime,
            endTime,
            pendingOnClient,
            identificationNumber,
            clientApplicationKind,
            workDescription,
            contractorWithContacts
        )

    fun createApplicationDto(
        id: Long? = 1,
        alluid: Int? = null,
        applicationType: ApplicationType = ApplicationType.CABLE_REPORT,
        applicationData: CableReportApplication = createCableReportApplication()
    ): ApplicationDto =
        ApplicationDto(
            id = id,
            alluid = alluid,
            applicationType = applicationType,
            applicationData = OBJECT_MAPPER.valueToTree(applicationData)
        )
}
