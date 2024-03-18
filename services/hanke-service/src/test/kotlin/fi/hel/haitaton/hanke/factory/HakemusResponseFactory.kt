package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.ApplicationArea
import fi.hel.haitaton.hanke.application.PostalAddress
import fi.hel.haitaton.hanke.application.StreetAddress
import fi.hel.haitaton.hanke.hakemus.ContactResponse
import fi.hel.haitaton.hanke.hakemus.CustomerResponse
import fi.hel.haitaton.hanke.hakemus.CustomerWithContactsResponse
import fi.hel.haitaton.hanke.hakemus.HakemusDataResponse
import fi.hel.haitaton.hanke.hakemus.HakemusResponse
import fi.hel.haitaton.hanke.hakemus.JohtoselvitysHakemusDataResponse
import java.time.ZonedDateTime
import java.util.UUID

object HakemusResponseFactory {

    private const val DEFAULT_STREET_NAME = "Kotikatu 1"

    fun create(
        applicationId: Long = 1,
        alluid: Int? = null,
        alluStatus: ApplicationStatus? = null,
        applicationIdentifier: String? = null,
        applicationData: HakemusDataResponse = createJohtoselvitysHakemusDataResponse(),
        hankeTunnus: String = "HAI-1234"
    ): HakemusResponse =
        HakemusResponse(
            applicationId,
            alluid,
            alluStatus,
            applicationIdentifier,
            applicationData.applicationType,
            applicationData,
            hankeTunnus
        )

    fun createJohtoselvitysHakemusDataResponse(
        pendingOnClient: Boolean = false,
        name: String = ApplicationFactory.DEFAULT_APPLICATION_NAME,
        postalAddress: PostalAddress = PostalAddress(StreetAddress(DEFAULT_STREET_NAME), "", ""),
        rockExcavation: Boolean = false,
        workDescription: String = ApplicationFactory.DEFAULT_WORK_DESCRIPTION,
        startTime: ZonedDateTime? = DateFactory.getStartDatetime(),
        endTime: ZonedDateTime? = DateFactory.getEndDatetime(),
        areas: List<ApplicationArea> = listOf(ApplicationFactory.createApplicationArea()),
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
    ): JohtoselvitysHakemusDataResponse =
        JohtoselvitysHakemusDataResponse(
            pendingOnClient = pendingOnClient,
            name = name,
            postalAddress = postalAddress,
            constructionWork = true,
            maintenanceWork = false,
            propertyConnectivity = false,
            emergencyWork = false,
            rockExcavation = rockExcavation,
            workDescription = workDescription,
            startTime = startTime,
            endTime = endTime,
            areas = areas,
            customerWithContacts = customerWithContacts,
            contractorWithContacts = contractorWithContacts,
            representativeWithContacts = representativeWithContacts,
            propertyDeveloperWithContacts = propertyDeveloperWithContacts,
        )

    fun companyCustomer(
        type: CustomerType = CustomerType.COMPANY,
        name: String = "DNA",
        email: String = "info@dna.test",
        phone: String = "+3581012345678",
        registryKey: String? = "3766028-0",
    ): CustomerResponse =
        CustomerResponse(
            UUID.randomUUID(),
            type,
            name,
            email,
            phone,
            registryKey,
        )

    fun personCustomer(
        type: CustomerType = CustomerType.PERSON,
        name: String = TEPPO_TESTI,
        email: String = ApplicationFactory.TEPPO_EMAIL,
        phone: String = ApplicationFactory.TEPPO_PHONE,
        registryKey: String? = "281192-937W",
    ) =
        CustomerResponse(
            UUID.randomUUID(),
            type,
            name,
            email,
            phone,
            registryKey,
        )

    fun CustomerResponse.withContacts(
        vararg contacts: ContactResponse
    ): CustomerWithContactsResponse = CustomerWithContactsResponse(this, contacts.asList())

    private fun createPersonCustomerResponse(
        yhteystietoId: UUID = UUID.randomUUID(),
        type: CustomerType = CustomerType.PERSON,
        name: String = TEPPO_TESTI,
        email: String = ApplicationFactory.TEPPO_EMAIL,
        phone: String = "04012345678",
        registryKey: String? = "281192-937W",
    ) =
        CustomerResponse(
            yhteystietoId,
            type,
            name,
            email,
            phone,
            registryKey,
        )

    private fun createCompanyCustomerResponse(
        yhteystietoId: UUID = UUID.randomUUID(),
        type: CustomerType = CustomerType.COMPANY,
        name: String = "DNA",
        email: String = "info@dna.test",
        phone: String = "+3581012345678",
        registryKey: String? = "3766028-0",
    ): CustomerResponse {
        return CustomerResponse(
            yhteystietoId,
            type,
            name,
            email,
            phone,
            registryKey,
        )
    }

    private fun createContactResponse(
        hankekayttajaId: UUID = UUID.randomUUID(),
        firstName: String = "Teppo",
        lastName: String = "Testihenkilö",
        email: String = ApplicationFactory.TEPPO_EMAIL,
        phone: String = "04012345678",
        orderer: Boolean = false
    ) = ContactResponse(hankekayttajaId, firstName, lastName, email, phone, orderer)
}
