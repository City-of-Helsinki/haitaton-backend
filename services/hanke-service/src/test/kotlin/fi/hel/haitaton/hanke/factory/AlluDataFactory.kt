package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.allu.AlluApplicationResponse
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.AttachmentMetadata
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.ApplicationArea
import fi.hel.haitaton.hanke.application.ApplicationData
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.CableReportWithoutHanke
import fi.hel.haitaton.hanke.application.Contact
import fi.hel.haitaton.hanke.application.Customer
import fi.hel.haitaton.hanke.application.CustomerWithContacts
import fi.hel.haitaton.hanke.application.PostalAddress
import fi.hel.haitaton.hanke.application.StreetAddress
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory.Companion.KAYTTAJA_INPUT_ASIANHOITAJA
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory.Companion.KAYTTAJA_INPUT_HAKIJA
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory.Companion.KAYTTAJA_INPUT_RAKENNUTTAJA
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory.Companion.KAYTTAJA_INPUT_SUORITTAJA
import java.time.ZonedDateTime
import org.geojson.Polygon
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE
import org.springframework.stereotype.Component

const val TEPPO_TESTI = "Teppo Testihenkilö"

@Component
class AlluDataFactory(
    private val applicationRepository: ApplicationRepository,
    private val hankeFactory: HankeFactory,
) {
    companion object {
        const val defaultApplicationId: Long = 1
        const val defaultApplicationName: String = "Johtoselvityksen oletusnimi"
        const val defaultApplicationIdentifier: String = "JS230014"
        const val teppoEmail = "teppo@example.test"
        val expectedRecipients =
            arrayOf(
                "timo.tyonsuorittaja@mail.com",
                "anssi.asianhoitaja@mail.com",
                "rane.rakennuttaja@mail.com",
                "new.mail@foo.fi",
            )

        fun createPostalAddress(
            streetAddress: String = "Katu 1",
            postalCode: String = "00100",
            city: String = "Helsinki",
        ) = PostalAddress(StreetAddress(streetAddress), postalCode, city)

        fun createPersonCustomer(
            type: CustomerType? = CustomerType.PERSON,
            name: String = TEPPO_TESTI,
            country: String = "FI",
            email: String? = teppoEmail,
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
            email: String? = "info@dna.test",
            phone: String? = "+3581012345678",
            registryKey: String? = "3766028-0",
            ovt: String? = null,
            invoicingOperator: String? = null,
            sapCustomerNumber: String? = null,
        ): Customer =
            Customer(
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

        fun createCompanyCustomerWithOrderer(): CustomerWithContacts {
            val customer = createCompanyCustomer()
            val contact = createContact(orderer = true)
            return CustomerWithContacts(customer, listOf(contact))
        }

        fun ApplicationEntity.withCustomer(customer: CustomerWithContacts): ApplicationEntity {
            applicationData =
                (applicationData as CableReportApplicationData).copy(
                    customerWithContacts = customer
                )
            return this
        }

        fun Customer.withContacts(vararg contacts: Contact): CustomerWithContacts =
            CustomerWithContacts(this, contacts.asList())

        fun Customer.withContact(
            firstName: String? = "Teppo",
            lastName: String? = "Testihenkilö",
            email: String? = teppoEmail,
            phone: String? = "04012345678",
            orderer: Boolean = false,
        ) = withContacts(createContact(firstName, lastName, email, phone, orderer))

        fun createContact(
            firstName: String? = "Teppo",
            lastName: String? = "Testihenkilö",
            email: String? = teppoEmail,
            phone: String? = "04012345678",
            orderer: Boolean = false
        ) = Contact(firstName, lastName, email, phone, orderer)

        fun createApplicationArea(
            name: String = "Area name",
            geometry: Polygon =
                "/fi/hel/haitaton/hanke/geometria/toinen_polygoni.json".asJsonResource(),
        ): ApplicationArea = ApplicationArea(name, geometry)

        fun Application.withApplicationData(
            name: String = defaultApplicationName,
            areas: List<ApplicationArea>? = listOf(createApplicationArea()),
            startTime: ZonedDateTime? = DateFactory.getStartDatetime(),
            endTime: ZonedDateTime? = DateFactory.getEndDatetime(),
            pendingOnClient: Boolean = false,
            workDescription: String = "Work description.",
            customerWithContacts: CustomerWithContacts =
                createCompanyCustomer().withContacts(createContact(orderer = true)),
            contractorWithContacts: CustomerWithContacts =
                createCompanyCustomer().withContacts(createContact()),
            representativeWithContacts: CustomerWithContacts? = null,
            propertyDeveloperWithContacts: CustomerWithContacts? = null,
            rockExcavation: Boolean = false,
            postalAddress: PostalAddress? = null,
        ): Application =
            this.copy(
                applicationData =
                    createCableReportApplicationData(
                        name,
                        areas,
                        startTime,
                        endTime,
                        pendingOnClient,
                        workDescription,
                        customerWithContacts,
                        contractorWithContacts,
                        representativeWithContacts,
                        propertyDeveloperWithContacts,
                        rockExcavation,
                        postalAddress
                    )
            )

        fun createCableReportApplicationData(
            name: String = defaultApplicationName,
            areas: List<ApplicationArea>? = listOf(createApplicationArea()),
            startTime: ZonedDateTime? = DateFactory.getStartDatetime(),
            endTime: ZonedDateTime? = DateFactory.getEndDatetime(),
            pendingOnClient: Boolean = false,
            workDescription: String = "Work description.",
            customerWithContacts: CustomerWithContacts =
                createCompanyCustomer().withContacts(createContact(orderer = true)),
            contractorWithContacts: CustomerWithContacts =
                createCompanyCustomer().withContacts(createContact()),
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

        fun Application.withCustomer(customer: CustomerWithContacts): Application =
            this.copy(
                applicationData =
                    (applicationData as CableReportApplicationData).copy(
                        customerWithContacts = customer
                    )
            )

        fun Application.withCustomerContacts(vararg contacts: Contact): Application =
            this.withCustomer(
                (applicationData as CableReportApplicationData)
                    .customerWithContacts
                    .copy(contacts = contacts.asList())
            )

        fun CableReportApplicationData.withPostalAddress(
            streetAddress: String = "Katu 1",
            postalCode: String = "00100",
            city: String = "Helsinki",
        ) = this.copy(postalAddress = PostalAddress(StreetAddress(streetAddress), postalCode, city))

        fun CableReportApplicationData.withArea(name: String, geometry: Polygon) =
            this.copy(areas = (areas ?: listOf()) + ApplicationArea(name, geometry))

        fun cableReportWithoutHanke(
            applicationData: CableReportApplicationData = createCableReportApplicationData(),
            applicationType: ApplicationType = ApplicationType.CABLE_REPORT,
            f: CableReportApplicationData.() -> CableReportApplicationData = { this },
        ): CableReportWithoutHanke = CableReportWithoutHanke(applicationType, applicationData.f())

        fun createApplications(
            n: Long,
            mapper: (Long, Application) -> Application = { _, app -> app },
        ) =
            (1..n)
                .map { i ->
                    createApplication(
                        id = i,
                        hankeTunnus = "HAI-1234",
                        applicationData =
                            createCableReportApplicationData(
                                name = "$defaultApplicationName #$i",
                                customerWithContacts =
                                    createCompanyCustomer(name = "Customer #$i")
                                        .withContacts(
                                            createContact(
                                                firstName = "Customer #$i",
                                                lastName = "Contact #$i",
                                            )
                                        ),
                                contractorWithContacts =
                                    createCompanyCustomer(name = "Contractor #$i")
                                        .withContacts(
                                            createContact(
                                                firstName = "Contractor #$i",
                                                lastName = "Contact #$i",
                                            )
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

        fun createAttachmentMetadata(
            id: Int? = null,
            mimeType: String = APPLICATION_PDF_VALUE,
            name: String = "file.pdf",
            description: String = "Test description."
        ) =
            AttachmentMetadata(
                id = id,
                mimeType = mimeType,
                name = name,
                description = description,
            )

        val hakijaCustomerContact: CustomerWithContacts =
            with(KAYTTAJA_INPUT_HAKIJA) {
                createCompanyCustomer()
                    .withContacts(
                        createContact(
                            firstName = etunimi,
                            lastName = sukunimi,
                            email = email,
                            orderer = true
                        )
                    )
            }

        val suorittajaCustomerContact: CustomerWithContacts =
            with(KAYTTAJA_INPUT_SUORITTAJA) {
                createCompanyCustomer()
                    .withContacts(
                        createContact(
                            firstName = etunimi,
                            lastName = sukunimi,
                            email = email,
                            orderer = false
                        )
                    )
            }

        val asianHoitajaCustomerContact: CustomerWithContacts =
            with(KAYTTAJA_INPUT_ASIANHOITAJA) {
                createCompanyCustomer()
                    .withContacts(
                        createContact(
                            firstName = etunimi,
                            lastName = sukunimi,
                            email = email,
                            orderer = false
                        )
                    )
            }

        val rakennuttajaCustomerContact: CustomerWithContacts =
            with(KAYTTAJA_INPUT_RAKENNUTTAJA) {
                createCompanyCustomer()
                    .withContacts(
                        createContact(
                            firstName = etunimi,
                            lastName = sukunimi,
                            email = email,
                            orderer = false
                        )
                    )
            }
    }

    /**
     * Save an application to database. The mutator can be used to mutate the entity before saving
     * it.
     */
    fun saveApplicationEntity(
        username: String,
        hanke: HankeEntity = hankeFactory.saveMinimal(),
        alluId: Int? = null,
        alluStatus: ApplicationStatus? = null,
        applicationIdentifier: String? = null,
        applicationType: ApplicationType = ApplicationType.CABLE_REPORT,
        applicationData: ApplicationData = createCableReportApplicationData(),
    ): ApplicationEntity {
        val applicationEntity =
            ApplicationEntity(
                id = null,
                alluid = alluId,
                alluStatus = alluStatus,
                applicationIdentifier = applicationIdentifier,
                userId = username,
                applicationType = applicationType,
                applicationData = applicationData,
                hanke = hanke,
            )
        return applicationRepository.save(applicationEntity)
    }

    /**
     * Save several applications to database. The mutator can be used to mutate the entities before
     * saving them.
     */
    fun saveApplicationEntities(
        n: Long,
        username: String,
        hanke: HankeEntity,
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
                    hanke = hanke,
                )
            }
        entities.withIndex().forEach { (i, application) -> mutator(i, application) }
        return applicationRepository.saveAll(entities)
    }
}
