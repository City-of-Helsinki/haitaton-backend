package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.allu.Application
import fi.hel.haitaton.hanke.allu.ApplicationEntity
import fi.hel.haitaton.hanke.allu.ApplicationRepository
import fi.hel.haitaton.hanke.allu.ApplicationType
import fi.hel.haitaton.hanke.allu.CableReportApplicationData
import fi.hel.haitaton.hanke.allu.Contact
import fi.hel.haitaton.hanke.allu.Customer
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.allu.CustomerWithContacts
import fi.hel.haitaton.hanke.allu.PostalAddress
import fi.hel.haitaton.hanke.allu.StreetAddress
import java.time.ZonedDateTime
import org.geojson.GeometryCollection
import org.springframework.stereotype.Component

@Component
class AlluDataFactory(val applicationRepository: ApplicationRepository) {
    companion object {
        const val defaultApplicationId: Long = 1
        const val defaultApplicationName: String = "Johtoselvitys"

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

        fun createCableReportApplicationData(
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
            applicationType: ApplicationType = ApplicationType.CABLE_REPORT,
            applicationData: CableReportApplicationData = createCableReportApplicationData(),
        ): Application =
            Application(
                id = id,
                alluid = alluid,
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
                                name = "Johtoselvitys #$i",
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
    }

    fun saveApplicationEntity(
        username: String,
        mapper: (ApplicationEntity) -> ApplicationEntity = { it },
    ): ApplicationEntity {
        val application = createApplication()
        val applicationEntity =
            ApplicationEntity(
                null,
                application.alluid,
                username,
                application.applicationType,
                application.applicationData
            )
        return applicationRepository.save(mapper(applicationEntity))
    }

    fun saveApplicationEntities(
        n: Long,
        username: String,
        mapper: (Int, ApplicationEntity) -> ApplicationEntity = { _, app -> app },
    ): List<ApplicationEntity> =
        applicationRepository.saveAll(
            createApplications(n)
                .map { application ->
                    ApplicationEntity(
                        id = null,
                        alluid = application.alluid,
                        userId = username,
                        applicationType = application.applicationType,
                        applicationData = application.applicationData,
                    )
                }
                .withIndex()
                .map { (i, application) -> mapper(i, application) }
        )
}
