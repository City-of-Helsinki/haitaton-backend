package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.allu.AlluApplicationResponse
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.ApplicationData
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.Contact
import fi.hel.haitaton.hanke.application.Customer
import fi.hel.haitaton.hanke.application.CustomerWithContacts
import fi.hel.haitaton.hanke.application.PostalAddress
import fi.hel.haitaton.hanke.application.StreetAddress
import java.time.ZonedDateTime
import org.geojson.GeometryCollection
import org.springframework.stereotype.Component

@Component
class AlluDataFactory(val applicationRepository: ApplicationRepository) {
    companion object {
        const val defaultApplicationId: Long = 1
        const val defaultApplicationName: String = "Johtoselvitys"
        const val defaultApplicationIdentifier: String = "JS230014"

        fun createPostalAddress(
            streetAddress: StreetAddress = StreetAddress("Katu 1"),
            postalCode: String = "00100",
            city: String = "Helsinki",
        ) = PostalAddress(streetAddress, postalCode, city)

        fun createPersonCustomer(
            type: CustomerType? = CustomerType.PERSON,
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
            type: CustomerType? = CustomerType.COMPANY,
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

        fun createCableReportApplicationData(
            name: String = defaultApplicationName,
            customerWithContacts: CustomerWithContacts =
                CustomerWithContacts(createCompanyCustomer(), listOf(createContact())),
            geometry: GeometryCollection = GeometryCollection(),
            startTime: ZonedDateTime? = DateFactory.getStartDatetime(),
            endTime: ZonedDateTime? = DateFactory.getEndDatetime(),
            pendingOnClient: Boolean = false,
            identificationNumber: String = "identification",
            clientApplicationKind: String = "applicationKind",
            workDescription: String = "Work description.",
            contractorWithContacts: CustomerWithContacts =
                CustomerWithContacts(createCompanyCustomer(), listOf(createContact())),
        ) =
            CableReportApplicationData(
                ApplicationType.CABLE_REPORT,
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

        fun createApplication(
            id: Long? = 1,
            alluid: Int? = null,
            alluStatus: ApplicationStatus? = null,
            applicationIdentifier: String? = null,
            applicationType: ApplicationType = ApplicationType.CABLE_REPORT,
            applicationData: CableReportApplicationData = createCableReportApplicationData(),
        ): Application =
            Application(
                id = id,
                alluid = alluid,
                alluStatus = alluStatus,
                applicationIdentifier = applicationIdentifier,
                applicationType = applicationType,
                applicationData = applicationData
            )

        fun createApplications(
            n: Long,
            mapper: (Long, Application) -> Application = { _, app -> app },
        ) =
            (1..n)
                .map { i ->
                    createApplication(
                        id = i,
                        applicationData =
                            createCableReportApplicationData(
                                name = "$defaultApplicationName #$i",
                                customerWithContacts =
                                    CustomerWithContacts(
                                        createCompanyCustomer(name = "Customer #$i"),
                                        listOf(createContact(name = "Customer #$i Contact"))
                                    ),
                                contractorWithContacts =
                                    CustomerWithContacts(
                                        createCompanyCustomer(name = "Contractor #$i"),
                                        listOf(createContact(name = "Contractor #$i Contact"))
                                    )
                            )
                    )
                }
                .map { application -> mapper(application.id!!, application) }

        fun createApplicationEntity(
            id: Long? = 3,
            alluid: Int? = null,
            alluStatus: ApplicationStatus? = null,
            applicationIdentifier: String? = null,
            userId: String? = null,
            applicationType: ApplicationType = ApplicationType.CABLE_REPORT,
            applicationData: ApplicationData = createCableReportApplicationData(),
        ): ApplicationEntity =
            ApplicationEntity(
                id,
                alluid,
                alluStatus,
                applicationIdentifier,
                userId,
                applicationType,
                applicationData,
            )

        fun createAlluApplicationResponse(
            id: Int = 42,
            status: ApplicationStatus = ApplicationStatus.PENDING
        ) =
            AlluApplicationResponse(
                id = id,
                name = defaultApplicationName,
                applicationId = defaultApplicationIdentifier,
                status = status,
                startTime = DateFactory.getStartDatetime(),
                endTime = DateFactory.getEndDatetime(),
                owner = null,
                kindsWithSpecifiers = mapOf(),
                terms = null,
                customerReference = null,
                surveyRequired = false
            )
    }

    /**
     * Save an application to database. The mutator can be used to mutate the entity before saving
     * it.
     */
    fun saveApplicationEntity(
        username: String,
        application: Application = createApplication(),
        mutator: (ApplicationEntity) -> Unit = {},
    ): ApplicationEntity {
        val applicationEntity =
            ApplicationEntity(
                id = null,
                alluid = application.alluid,
                alluStatus = null,
                applicationIdentifier = null,
                username,
                application.applicationType,
                application.applicationData
            )
        mutator(applicationEntity)
        return applicationRepository.save(applicationEntity)
    }

    /**
     * Save several applications to database. The mutator can be used to mutate the entities before
     * saving them.
     */
    fun saveApplicationEntities(
        n: Long,
        username: String,
        mutator: (Int, ApplicationEntity) -> Unit = { _, _ -> },
    ): List<ApplicationEntity> {
        val entities =
            createApplications(n).map { application ->
                ApplicationEntity(
                    id = null,
                    alluid = application.alluid,
                    alluStatus = null,
                    applicationIdentifier = null,
                    userId = username,
                    applicationType = application.applicationType,
                    applicationData = application.applicationData,
                )
            }
        entities.withIndex().forEach { (i, application) -> mutator(i, application) }
        return applicationRepository.saveAll(entities)
    }
}
