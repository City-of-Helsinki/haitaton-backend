package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.allu.ApplicationStatus
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
import fi.hel.haitaton.hanke.application.ExcavationNotificationApplicationData
import fi.hel.haitaton.hanke.application.PostalAddress
import fi.hel.haitaton.hanke.application.StreetAddress
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory.Companion.KAYTTAJA_INPUT_ASIANHOITAJA
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory.Companion.KAYTTAJA_INPUT_HAKIJA
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory.Companion.KAYTTAJA_INPUT_RAKENNUTTAJA
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory.Companion.KAYTTAJA_INPUT_SUORITTAJA
import java.time.ZonedDateTime
import org.geojson.Polygon
import org.springframework.stereotype.Component

const val TEPPO_TESTI = "Teppo Testihenkilö"

@Component
class ApplicationFactory(
    private val applicationRepository: ApplicationRepository,
    private val hankeFactory: HankeFactory,
) {
    companion object {
        const val DEFAULT_APPLICATION_ID: Long = 1
        const val DEFAULT_APPLICATION_NAME: String = "Hakemuksen oletusnimi"
        const val DEFAULT_APPLICATION_IDENTIFIER: String = "JS230014"
        const val TEPPO = "Teppo"
        const val TESTIHENKILO = "Testihenkilö"
        const val TEPPO_EMAIL = "teppo@example.test"
        const val TEPPO_PHONE = "04012345678"

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
            postalAddress: PostalAddress? = null,
            email: String? = TEPPO_EMAIL,
            phone: String? = TEPPO_PHONE,
            registryKey: String? = "281192-937W",
            ovt: String? = null,
            invoicingOperator: String? = null,
            country: String = "FI",
            sapCustomerNumber: String? = null,
        ) =
            Customer(
                type,
                name,
                postalAddress,
                email,
                phone,
                registryKey,
                ovt,
                invoicingOperator,
                country,
                sapCustomerNumber
            )

        fun createCompanyCustomer(
            type: CustomerType? = CustomerType.COMPANY,
            name: String = "DNA",
            postalAddress: PostalAddress? = null,
            email: String? = "info@dna.test",
            phone: String? = "+3581012345678",
            registryKey: String? = "3766028-0",
            ovt: String? = null,
            invoicingOperator: String? = null,
            country: String = "FI",
            sapCustomerNumber: String? = null,
        ): Customer =
            Customer(
                type,
                name,
                postalAddress,
                email,
                phone,
                registryKey,
                ovt,
                invoicingOperator,
                country,
                sapCustomerNumber
            )

        fun createCompanyCustomerWithOrderer(): CustomerWithContacts {
            val customer = createCompanyCustomer()
            val contact = createContact(orderer = true)
            return CustomerWithContacts(customer, listOf(contact))
        }

        private fun createCompanyInvoiceCustomer(): Customer =
            createCompanyCustomer(
                postalAddress = createPostalAddress(),
                ovt = "003737660280",
                invoicingOperator = "003721291126",
            )

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
            firstName: String? = TEPPO,
            lastName: String? = TESTIHENKILO,
            email: String? = TEPPO_EMAIL,
            phone: String? = TEPPO_PHONE,
            orderer: Boolean = false,
        ) = withContacts(createContact(firstName, lastName, email, phone, orderer))

        fun createContact(
            firstName: String? = TEPPO,
            lastName: String? = TESTIHENKILO,
            email: String? = TEPPO_EMAIL,
            phone: String? = TEPPO_PHONE,
            orderer: Boolean = false
        ) = Contact(firstName, lastName, email, phone, orderer)

        fun createApplicationArea(
            name: String = "Alue",
            geometry: Polygon = GeometriaFactory.secondPolygon,
        ): ApplicationArea = ApplicationArea(name, geometry)

        fun Application.withApplicationData(
            type: ApplicationType = ApplicationType.CABLE_REPORT,
            name: String = DEFAULT_APPLICATION_NAME,
            areas: List<ApplicationArea>? = listOf(createApplicationArea()),
            startTime: ZonedDateTime? = DateFactory.getStartDatetime(),
            endTime: ZonedDateTime? = DateFactory.getEndDatetime(),
            pendingOnClient: Boolean = false,
            workDescription: String = "Työn kuvaus.",
            customerWithContacts: CustomerWithContacts =
                createCompanyCustomer().withContacts(createContact(orderer = true)),
            contractorWithContacts: CustomerWithContacts =
                createCompanyCustomer().withContacts(createContact()),
            representativeWithContacts: CustomerWithContacts? = null,
            propertyDeveloperWithContacts: CustomerWithContacts? = null,
            rockExcavation: Boolean = false,
            postalAddress: PostalAddress? = null,
        ): Application =
            when (type) {
                ApplicationType.CABLE_REPORT ->
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
                ApplicationType.EXCAVATION_NOTIFICATION ->
                    this.copy(
                        applicationData =
                            createExcavationNotificationApplicationData(
                                pendingOnClient,
                                name,
                                workDescription,
                                false,
                                false,
                                false,
                                null,
                                null,
                                null,
                                false,
                                areas,
                                startTime,
                                endTime,
                                customerWithContacts,
                                contractorWithContacts,
                                representativeWithContacts,
                                propertyDeveloperWithContacts,
                                null,
                                null
                            )
                    )
            }

        fun createApplicationData(applicationType: ApplicationType): ApplicationData =
            when (applicationType) {
                ApplicationType.CABLE_REPORT -> createCableReportApplicationData()
                ApplicationType.EXCAVATION_NOTIFICATION ->
                    createExcavationNotificationApplicationData()
            }

        fun createCableReportApplicationData(
            name: String = DEFAULT_APPLICATION_NAME,
            areas: List<ApplicationArea>? = listOf(createApplicationArea()),
            startTime: ZonedDateTime? = DateFactory.getStartDatetime(),
            endTime: ZonedDateTime? = DateFactory.getEndDatetime(),
            pendingOnClient: Boolean = false,
            workDescription: String = "Työn kuvaus.",
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

        fun createExcavationNotificationApplicationData(
            pendingOnClient: Boolean = false,
            name: String = DEFAULT_APPLICATION_NAME,
            workDescription: String = "Työn kuvaus.",
            maintenanceWork: Boolean = false,
            emergencyWork: Boolean = false,
            cableReportDone: Boolean = false,
            rockExcavation: Boolean? = null,
            cableReports: List<String>? = null,
            placementContracts: List<String>? = null,
            requiredCompetence: Boolean = false,
            areas: List<ApplicationArea>? = listOf(createApplicationArea()),
            startTime: ZonedDateTime? = DateFactory.getStartDatetime(),
            endTime: ZonedDateTime? = DateFactory.getEndDatetime(),
            customerWithContacts: CustomerWithContacts =
                createCompanyCustomer().withContacts(createContact(orderer = true)),
            contractorWithContacts: CustomerWithContacts =
                createCompanyCustomer().withContacts(createContact()),
            representativeWithContacts: CustomerWithContacts? = null,
            propertyDeveloperWithContacts: CustomerWithContacts? = null,
            invoicingCustomer: Customer? = createCompanyInvoiceCustomer(),
            customerReference: String? = "Asiakkaan viite",
            additionalInfo: String? = null,
        ): ExcavationNotificationApplicationData =
            ExcavationNotificationApplicationData(
                applicationType = ApplicationType.EXCAVATION_NOTIFICATION,
                pendingOnClient = pendingOnClient,
                name = name,
                workDescription = workDescription,
                maintenanceWork = maintenanceWork,
                emergencyWork = emergencyWork,
                cableReportDone = cableReportDone,
                rockExcavation = rockExcavation,
                cableReports = cableReports,
                placementContracts = placementContracts,
                requiredCompetence = requiredCompetence,
                areas = areas,
                startTime = startTime,
                endTime = endTime,
                customerWithContacts = customerWithContacts,
                contractorWithContacts = contractorWithContacts,
                representativeWithContacts = representativeWithContacts,
                propertyDeveloperWithContacts = propertyDeveloperWithContacts,
                invoicingCustomer = invoicingCustomer,
                customerReference = customerReference,
                additionalInfo = additionalInfo,
            )

        fun createBlankApplicationData(applicationType: ApplicationType): ApplicationData =
            when (applicationType) {
                ApplicationType.CABLE_REPORT -> createBlankCableReportApplicationData()
                ApplicationType.EXCAVATION_NOTIFICATION ->
                    createBlankExcavationNotificationApplicationData()
            }

        internal fun createBlankCableReportApplicationData() =
            createCableReportApplicationData(
                name = "",
                workDescription = "",
                startTime = null,
                endTime = null,
                areas = null,
                customerWithContacts =
                    CustomerWithContacts(
                        Customer(null, "", null, "", null, null, null, null, "", null),
                        listOf()
                    ),
                contractorWithContacts =
                    CustomerWithContacts(
                        Customer(null, "", null, "", null, null, null, null, "", null),
                        listOf()
                    ),
                postalAddress = PostalAddress(StreetAddress(""), "", "")
            )

        internal fun createBlankExcavationNotificationApplicationData() =
            createExcavationNotificationApplicationData(
                name = "",
                workDescription = "",
                areas = null,
                startTime = null,
                endTime = null,
                customerWithContacts =
                    CustomerWithContacts(
                        Customer(null, "", null, "", null, null, null, null, "", null),
                        listOf()
                    ),
                contractorWithContacts =
                    CustomerWithContacts(
                        Customer(null, "", null, "", null, null, null, null, "", null),
                        listOf()
                    ),
                additionalInfo = null
            )

        fun createApplication(
            id: Long? = 1,
            alluid: Int? = null,
            alluStatus: ApplicationStatus? = null,
            applicationIdentifier: String? = null,
            applicationType: ApplicationType = ApplicationType.CABLE_REPORT,
            applicationData: ApplicationData = createApplicationData(applicationType),
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

        fun Application.withCustomer(customer: CustomerWithContacts?): Application =
            this.copy(
                applicationData =
                    (applicationData as CableReportApplicationData).copy(
                        customerWithContacts = customer
                    )
            )

        fun Application.withCustomerContacts(vararg contacts: Contact): Application =
            this.withCustomer(
                (applicationData as CableReportApplicationData)
                    .customerWithContacts!!
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
                                name = "$DEFAULT_APPLICATION_NAME #$i",
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

        fun createApplicationEntities(
            n: Long,
            hanke: HankeEntity = HankeFactory.createMinimalEntity(),
            applicationType: ApplicationType = ApplicationType.CABLE_REPORT,
        ) =
            (1..n).map { i ->
                createApplicationEntity(id = i, hanke = hanke, applicationType = applicationType)
            }

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

    /** Save an application to database. it. */
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
        entities.forEachIndexed { i, application -> mutator(i, application) }
        return applicationRepository.saveAll(entities)
    }
}
