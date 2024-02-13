package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.ApplicationArea
import fi.hel.haitaton.hanke.application.ApplicationData
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.application.ApplicationService
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.CustomerWithContacts
import fi.hel.haitaton.hanke.application.PostalAddress
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.hakemus.ContactResponse
import fi.hel.haitaton.hanke.hakemus.CustomerResponse
import fi.hel.haitaton.hanke.hakemus.CustomerWithContactsResponse
import fi.hel.haitaton.hanke.hakemus.HakemusResponse
import fi.hel.haitaton.hanke.hakemus.HakemusyhteyshenkiloRepository
import fi.hel.haitaton.hanke.hakemus.HakemusyhteystietoRepository
import fi.hel.haitaton.hanke.hakemus.JohtoselvitysHakemusDataResponse
import fi.hel.haitaton.hanke.profiili.ProfiiliClient
import java.time.ZonedDateTime
import java.util.UUID
import org.geojson.Polygon
import org.springframework.stereotype.Component

@Component
class HakemusFactory(
    private val applicationService: ApplicationService,
    private val profiiliClient: ProfiiliClient,
    private val applicationRepository: ApplicationRepository,
    private val hakemusyhteystietoRepository: HakemusyhteystietoRepository,
    private val hakemusyhteyshenkiloRepository: HakemusyhteyshenkiloRepository,
    private val hankeKayttajaFactory: HankeKayttajaFactory,
) {
    companion object {
        private const val DEFAULT_APPLICATION_NAME: String = "Johtoselvityksen oletusnimi"
        private const val TEPPO_EMAIL = "teppo@example.test"

        private fun createPersonCustomerResponse(
            yhteystietoId: UUID = UUID.randomUUID(),
            type: CustomerType? = CustomerType.PERSON,
            name: String = TEPPO_TESTI,
            country: String = "FI",
            email: String? = TEPPO_EMAIL,
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

        fun CustomerResponse.withContacts(
            vararg contacts: ContactResponse
        ): CustomerWithContactsResponse = CustomerWithContactsResponse(this, contacts.asList())

        private fun createContactResponse(
            hankekayttajaId: UUID = UUID.randomUUID(),
            firstName: String? = "Teppo",
            lastName: String? = "Testihenkilö",
            email: String? = TEPPO_EMAIL,
            phone: String? = "04012345678",
            orderer: Boolean = false
        ) = ContactResponse(hankekayttajaId, firstName, lastName, email, phone, orderer)

        private fun createApplicationArea(
            name: String = "Area name",
            geometry: Polygon =
                "/fi/hel/haitaton/hanke/geometria/toinen_polygoni.json".asJsonResource(),
        ): ApplicationArea = ApplicationArea(name, geometry)

        fun createCableReportApplicationData(
            name: String = DEFAULT_APPLICATION_NAME,
            areas: List<ApplicationArea>? = listOf(createApplicationArea()),
            startTime: ZonedDateTime? = DateFactory.getStartDatetime(),
            endTime: ZonedDateTime? = DateFactory.getEndDatetime(),
            pendingOnClient: Boolean = false,
            workDescription: String = "Work description.",
            customerWithContacts: CustomerWithContacts = CustomerWithContacts(),
            contractorWithContacts: CustomerWithContacts = CustomerWithContacts(),
            representativeWithContacts: CustomerWithContacts? = null,
            propertyDeveloperWithContacts: CustomerWithContacts? = null,
            rockExcavation: Boolean = false,
            postalAddress: PostalAddress? = null,
        ): CableReportApplicationData =
            CableReportApplicationData(
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

        private fun createJohtoselvitysHakemusDataResponse(
            name: String = DEFAULT_APPLICATION_NAME,
            areas: List<ApplicationArea>? = listOf(createApplicationArea()),
            startTime: ZonedDateTime? = DateFactory.getStartDatetime(),
            endTime: ZonedDateTime? = DateFactory.getEndDatetime(),
            pendingOnClient: Boolean = false,
            workDescription: String = "Work description.",
            customerWithContacts: CustomerWithContactsResponse =
                CustomerWithContactsResponse(
                    createCompanyCustomerResponse(),
                    listOf(createContactResponse())
                ),
            contractorWithContacts: CustomerWithContactsResponse =
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

        fun createApplication(
            id: Long? = 1,
            alluid: Int? = null,
            alluStatus: ApplicationStatus? = null,
            applicationIdentifier: String? = null,
            applicationType: ApplicationType = ApplicationType.CABLE_REPORT,
            applicationData: ApplicationData = createCableReportApplicationData(),
            hankeTunnus: String = "HAI-1234",
        ): Application =
            Application(
                id = id,
                alluid = alluid,
                alluStatus = alluStatus,
                applicationIdentifier = applicationIdentifier,
                applicationType = applicationType,
                applicationData = applicationData,
                hankeTunnus = hankeTunnus
            )

        fun createApplicationEntity(
            id: Long? = 3,
            alluid: Int? = null,
            alluStatus: ApplicationStatus? = null,
            applicationIdentifier: String? = null,
            userId: String? = null,
            applicationType: ApplicationType = ApplicationType.CABLE_REPORT,
            applicationData: ApplicationData = createCableReportApplicationData(),
            hanke: HankeEntity,
        ): ApplicationEntity =
            ApplicationEntity(
                id,
                alluid,
                alluStatus,
                applicationIdentifier,
                userId,
                applicationType,
                applicationData,
                hanke = hanke,
            )

        fun createHakemusResponse(applicationId: Long, hankeTunnus: String): HakemusResponse =
            HakemusResponse(
                applicationId,
                null,
                null,
                null,
                ApplicationType.CABLE_REPORT,
                createJohtoselvitysHakemusDataResponse(),
                hankeTunnus
            )
    }

    fun builder(userId: String, hankeEntity: HankeEntity): ApplicationBuilder {
        val application = createApplication(hankeTunnus = hankeEntity.hankeTunnus)
        return ApplicationBuilder(
            application,
            userId,
            ProfiiliFactory.DEFAULT_NAMES,
            applicationService,
            applicationRepository,
            profiiliClient,
            hankeKayttajaFactory,
            hakemusyhteystietoRepository,
            hakemusyhteyshenkiloRepository,
        )
    }
}
