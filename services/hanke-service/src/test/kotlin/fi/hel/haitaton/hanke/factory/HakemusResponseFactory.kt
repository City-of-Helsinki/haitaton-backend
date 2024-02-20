package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.ApplicationArea
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.application.PostalAddress
import fi.hel.haitaton.hanke.hakemus.ContactResponse
import fi.hel.haitaton.hanke.hakemus.CustomerResponse
import fi.hel.haitaton.hanke.hakemus.CustomerWithContactsResponse
import fi.hel.haitaton.hanke.hakemus.HakemusDataResponse
import fi.hel.haitaton.hanke.hakemus.HakemusResponse
import fi.hel.haitaton.hanke.hakemus.JohtoselvitysHakemusDataResponse
import java.time.ZonedDateTime
import java.util.UUID

object HakemusResponseFactory {

    fun create(
        applicationId: Long = 1,
        alluid: Int? = null,
        alluStatus: ApplicationStatus? = null,
        applicationIdentifier: String? = null,
        applicationType: ApplicationType = ApplicationType.CABLE_REPORT,
        applicationData: HakemusDataResponse = createJohtoselvitysHakemusDataResponse(),
        hankeTunnus: String = "HAI-1234"
    ): HakemusResponse =
        HakemusResponse(
            applicationId,
            alluid,
            alluStatus,
            applicationIdentifier,
            applicationType,
            applicationData,
            hankeTunnus
        )

    fun createJohtoselvitysHakemusDataResponse(
        name: String = ApplicationFactory.DEFAULT_APPLICATION_NAME,
        areas: List<ApplicationArea>? = listOf(ApplicationFactory.createApplicationArea()),
        startTime: ZonedDateTime? = DateFactory.getStartDatetime(),
        endTime: ZonedDateTime? = DateFactory.getEndDatetime(),
        pendingOnClient: Boolean = false,
        workDescription: String = "Work description.",
        customerWithContacts: CustomerWithContactsResponse? =
            CustomerWithContactsResponse(
                createCompanyCustomerResponse(),
                listOf(createContactResponse())
            ),
        contractorWithContacts: CustomerWithContactsResponse? =
            CustomerWithContactsResponse(
                createPersonCustomerResponse(),
                listOf(createContactResponse())
            ),
        representativeWithContacts: CustomerWithContactsResponse? = null,
        propertyDeveloperWithContacts: CustomerWithContactsResponse? = null,
        rockExcavation: Boolean = false,
        postalAddress: PostalAddress? = null,
    ): JohtoselvitysHakemusDataResponse =
        JohtoselvitysHakemusDataResponse(
            applicationType = ApplicationType.CABLE_REPORT,
            name = name,
            areas = areas,
            startTime = startTime,
            endTime = endTime,
            pendingOnClient = pendingOnClient,
            workDescription = workDescription,
            customerWithContacts = customerWithContacts,
            contractorWithContacts = contractorWithContacts,
            representativeWithContacts = representativeWithContacts,
            propertyDeveloperWithContacts = propertyDeveloperWithContacts,
            rockExcavation = rockExcavation,
            postalAddress = postalAddress,
        )

    fun companyCustomer(
        type: CustomerType? = CustomerType.COMPANY,
        name: String = "DNA",
        country: String = "FI",
        email: String? = "info@dna.test",
        phone: String? = "+3581012345678",
        registryKey: String? = "3766028-0",
        ovt: String? = null,
        invoicingOperator: String? = null,
        sapCustomerNumber: String? = null,
    ): CustomerResponse =
        CustomerResponse(
            UUID.randomUUID(),
            type,
            name,
            country,
            email,
            phone,
            registryKey,
            ovt,
            invoicingOperator,
            sapCustomerNumber
        )

    fun personCustomer(
        type: CustomerType? = CustomerType.PERSON,
        name: String = TEPPO_TESTI,
        country: String = "FI",
        email: String? = ApplicationFactory.TEPPO_EMAIL,
        phone: String? = ApplicationFactory.TEPPO_PHONE,
        registryKey: String? = "281192-937W",
        ovt: String? = null,
        invoicingOperator: String? = null,
        sapCustomerNumber: String? = null,
    ) =
        CustomerResponse(
            UUID.randomUUID(),
            type,
            name,
            country,
            email,
            phone,
            registryKey,
            ovt,
            invoicingOperator,
            sapCustomerNumber
        )

    fun CustomerResponse.withContacts(
        vararg contacts: ContactResponse
    ): CustomerWithContactsResponse = CustomerWithContactsResponse(this, contacts.asList())

    private fun createPersonCustomerResponse(
        yhteystietoId: UUID = UUID.randomUUID(),
        type: CustomerType? = CustomerType.PERSON,
        name: String = TEPPO_TESTI,
        country: String = "FI",
        email: String? = ApplicationFactory.TEPPO_EMAIL,
        phone: String? = "04012345678",
        registryKey: String? = "281192-937W",
        ovt: String? = null,
        invoicingOperator: String? = null,
        sapCustomerNumber: String? = null,
    ) =
        CustomerResponse(
            yhteystietoId,
            type,
            name,
            country,
            email,
            phone,
            registryKey,
            ovt,
            invoicingOperator,
            sapCustomerNumber
        )

    private fun createCompanyCustomerResponse(
        yhteystietoId: UUID = UUID.randomUUID(),
        type: CustomerType? = CustomerType.COMPANY,
        name: String = "DNA",
        country: String = "FI",
        email: String? = "info@dna.test",
        phone: String? = "+3581012345678",
        registryKey: String? = "3766028-0",
        ovt: String? = null,
        invoicingOperator: String? = null,
        sapCustomerNumber: String? = null,
    ): CustomerResponse {
        return CustomerResponse(
            yhteystietoId,
            type,
            name,
            country,
            email,
            phone,
            registryKey,
            ovt,
            invoicingOperator,
            sapCustomerNumber
        )
    }

    private fun createContactResponse(
        hankekayttajaId: UUID = UUID.randomUUID(),
        firstName: String? = "Teppo",
        lastName: String? = "Testihenkilö",
        email: String? = ApplicationFactory.TEPPO_EMAIL,
        phone: String? = "04012345678",
        orderer: Boolean = false
    ) = ContactResponse(hankekayttajaId, firstName, lastName, email, phone, orderer)
}
