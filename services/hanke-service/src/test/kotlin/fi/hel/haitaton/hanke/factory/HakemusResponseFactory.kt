package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hakemus.ContactResponse
import fi.hel.haitaton.hanke.hakemus.CustomerResponse
import fi.hel.haitaton.hanke.hakemus.CustomerWithContactsResponse
import fi.hel.haitaton.hanke.hakemus.HakemusDataResponse
import fi.hel.haitaton.hanke.hakemus.HakemusResponse
import fi.hel.haitaton.hanke.hakemus.InvoicingCustomerResponse
import fi.hel.haitaton.hanke.hakemus.JohtoselvitysHakemusDataResponse
import fi.hel.haitaton.hanke.hakemus.JohtoselvitysHakemusalue
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusAlue
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusDataResponse
import fi.hel.haitaton.hanke.hakemus.PostalAddress
import fi.hel.haitaton.hanke.hakemus.StreetAddress
import fi.hel.haitaton.hanke.valmistumisilmoitus.ValmistumisilmoitusResponse
import java.time.ZonedDateTime
import java.util.UUID

object HakemusResponseFactory {

    private const val DEFAULT_STREET_NAME = "Kotikatu 1"

    fun create(
        applicationType: ApplicationType = ApplicationType.CABLE_REPORT,
        applicationId: Long = 1,
        alluid: Int? = null,
        alluStatus: ApplicationStatus? = null,
        applicationIdentifier: String? = null,
        applicationData: HakemusDataResponse = createHakemusDataResponse(applicationType),
        hankeTunnus: String = "HAI-1234",
        valmistumisilmoitukset: List<ValmistumisilmoitusResponse> = listOf(),
    ): HakemusResponse =
        HakemusResponse(
            applicationId,
            alluid,
            alluStatus,
            applicationIdentifier,
            applicationData.applicationType,
            applicationData,
            hankeTunnus,
            valmistumisilmoitukset.groupBy { it.type },
        )

    private fun createHakemusDataResponse(applicationType: ApplicationType): HakemusDataResponse =
        when (applicationType) {
            ApplicationType.CABLE_REPORT -> createJohtoselvitysHakemusDataResponse()
            ApplicationType.EXCAVATION_NOTIFICATION -> createKaivuilmoitusDataResponse()
        }

    fun createJohtoselvitysHakemusDataResponse(
        name: String = ApplicationFactory.DEFAULT_APPLICATION_NAME,
        postalAddress: PostalAddress = PostalAddress(StreetAddress(DEFAULT_STREET_NAME), "", ""),
        rockExcavation: Boolean = false,
        workDescription: String = ApplicationFactory.DEFAULT_WORK_DESCRIPTION,
        startTime: ZonedDateTime? = DateFactory.getStartDatetime(),
        endTime: ZonedDateTime? = DateFactory.getEndDatetime(),
        areas: List<JohtoselvitysHakemusalue> =
            listOf(ApplicationFactory.createCableReportApplicationArea()),
        customerWithContacts: CustomerWithContactsResponse? =
            CustomerWithContactsResponse(companyCustomer(), listOf(createContactResponse())),
        contractorWithContacts: CustomerWithContactsResponse? =
            CustomerWithContactsResponse(personCustomer(), listOf(createContactResponse())),
        representativeWithContacts: CustomerWithContactsResponse? = null,
        propertyDeveloperWithContacts: CustomerWithContactsResponse? = null,
    ): JohtoselvitysHakemusDataResponse =
        JohtoselvitysHakemusDataResponse(
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
            paperDecisionReceiver = null,
            customerWithContacts = customerWithContacts,
            contractorWithContacts = contractorWithContacts,
            representativeWithContacts = representativeWithContacts,
            propertyDeveloperWithContacts = propertyDeveloperWithContacts,
        )

    private fun createKaivuilmoitusDataResponse(
        name: String = ApplicationFactory.DEFAULT_APPLICATION_NAME,
        workDescription: String = "Work description.",
        cableReportDone: Boolean = false,
        rockExcavation: Boolean = false,
        cableReports: List<String> = emptyList(),
        placementContracts: List<String> = emptyList(),
        requiredCompetence: Boolean = false,
        startTime: ZonedDateTime? = DateFactory.getStartDatetime(),
        endTime: ZonedDateTime? = DateFactory.getEndDatetime(),
        areas: List<KaivuilmoitusAlue> =
            listOf(ApplicationFactory.createExcavationNotificationArea()),
        customerWithContacts: CustomerWithContactsResponse? =
            CustomerWithContactsResponse(companyCustomer(), listOf(createContactResponse())),
        contractorWithContacts: CustomerWithContactsResponse? =
            CustomerWithContactsResponse(personCustomer(), listOf(createContactResponse())),
        representativeWithContacts: CustomerWithContactsResponse? = null,
        propertyDeveloperWithContacts: CustomerWithContactsResponse? = null,
        invoicingCustomer: InvoicingCustomerResponse? = createInvoicingCustomerResponse(),
        additionalInfo: String? = null,
    ): KaivuilmoitusDataResponse =
        KaivuilmoitusDataResponse(
            name = name,
            workDescription = workDescription,
            constructionWork = true,
            maintenanceWork = false,
            emergencyWork = false,
            cableReportDone = cableReportDone,
            rockExcavation = rockExcavation,
            cableReports = cableReports,
            placementContracts = placementContracts,
            requiredCompetence = requiredCompetence,
            startTime = startTime,
            endTime = endTime,
            areas = areas,
            paperDecisionReceiver = null,
            customerWithContacts = customerWithContacts,
            contractorWithContacts = contractorWithContacts,
            representativeWithContacts = representativeWithContacts,
            propertyDeveloperWithContacts = propertyDeveloperWithContacts,
            invoicingCustomer = invoicingCustomer,
            additionalInfo = additionalInfo,
        )

    fun companyCustomer(
        yhteystietoId: UUID = UUID.randomUUID(),
        type: CustomerType = CustomerType.COMPANY,
        name: String = "DNA",
        email: String = "info@dna.test",
        phone: String = "+3581012345678",
        registryKey: String? = "3766028-0",
    ): CustomerResponse =
        CustomerResponse(
            yhteystietoId = yhteystietoId,
            type = type,
            name = name,
            email = email,
            phone = phone,
            registryKey = registryKey,
            registryKeyHidden = false,
        )

    fun personCustomer(
        yhteystietoId: UUID = UUID.randomUUID(),
        type: CustomerType = CustomerType.PERSON,
        name: String = TEPPO_TESTI,
        email: String = ApplicationFactory.TEPPO_EMAIL,
        phone: String = ApplicationFactory.TEPPO_PHONE,
    ) =
        CustomerResponse(
            yhteystietoId = yhteystietoId,
            type = type,
            name = name,
            email = email,
            phone = phone,
            registryKey = null,
            registryKeyHidden = true,
        )

    fun CustomerResponse.withContacts(
        vararg contacts: ContactResponse
    ): CustomerWithContactsResponse = CustomerWithContactsResponse(this, contacts.asList())

    private fun createContactResponse(
        hankekayttajaId: UUID = UUID.randomUUID(),
        firstName: String = "Teppo",
        lastName: String = "Testihenkilö",
        email: String = ApplicationFactory.TEPPO_EMAIL,
        phone: String = "04012345678",
        orderer: Boolean = false,
    ) = ContactResponse(hankekayttajaId, firstName, lastName, email, phone, orderer)

    private fun createInvoicingCustomerResponse(
        type: CustomerType = CustomerType.COMPANY,
        name: String = "Laskutus Oy",
        registryKey: String? = "3766028-0",
        registryKeyHidden: Boolean = false,
        ovt: String = "003737660280",
        invoicingOperator: String = "003711223344",
        customerReference: String = "1234567890",
        postalAddress: PostalAddress =
            PostalAddress(StreetAddress(DEFAULT_STREET_NAME), "00100", "Helsinki"),
        email: String = "laskutus@dna.test",
        phone: String = "+3581012345678",
    ): InvoicingCustomerResponse =
        InvoicingCustomerResponse(
            type,
            name,
            registryKey,
            registryKeyHidden,
            ovt,
            invoicingOperator,
            customerReference,
            postalAddress,
            email,
            phone,
        )
}
