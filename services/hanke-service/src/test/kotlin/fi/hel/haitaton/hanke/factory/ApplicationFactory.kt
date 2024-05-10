package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.ApplicationData
import fi.hel.haitaton.hanke.application.ApplicationEntity
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.application.ApplicationType
import fi.hel.haitaton.hanke.application.CableReportApplicationArea
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.Contact
import fi.hel.haitaton.hanke.application.Customer
import fi.hel.haitaton.hanke.application.CustomerWithContacts
import fi.hel.haitaton.hanke.application.ExcavationNotificationArea
import fi.hel.haitaton.hanke.application.ExcavationNotificationData
import fi.hel.haitaton.hanke.application.InvoicingCustomer
import fi.hel.haitaton.hanke.application.PostalAddress
import fi.hel.haitaton.hanke.application.StreetAddress
import fi.hel.haitaton.hanke.application.Tyoalue
import fi.hel.haitaton.hanke.domain.TyomaaTyyppi
import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.tormaystarkastelu.Meluhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Polyhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Tarinahaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import fi.hel.haitaton.hanke.tormaystarkastelu.VaikutusAutoliikenteenKaistamaariin
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
        const val DEFAULT_WORK_DESCRIPTION: String = "Työn kuvaus."
        const val TEPPO = "Teppo"
        const val TESTIHENKILO = "Testihenkilö"
        const val TEPPO_EMAIL = "teppo@example.test"
        const val TEPPO_PHONE = "04012345678"

        fun createPostalAddress(
            streetAddress: String = "Katu 1",
            postalCode: String = "00100",
            city: String = "Helsinki",
        ) = PostalAddress(StreetAddress(streetAddress), postalCode, city)

        fun createPersonCustomer(
            type: CustomerType? = CustomerType.PERSON,
            name: String = TEPPO_TESTI,
            email: String? = TEPPO_EMAIL,
            phone: String? = TEPPO_PHONE,
            registryKey: String? = "281192-937W",
        ) =
            Customer(
                type,
                name,
                email,
                phone,
                registryKey,
            )

        fun createCompanyCustomer(
            type: CustomerType? = CustomerType.COMPANY,
            name: String = "DNA",
            email: String? = "info@dna.test",
            phone: String? = "+3581012345678",
            registryKey: String? = "3766028-0",
        ): Customer =
            Customer(
                type,
                name,
                email,
                phone,
                registryKey,
            )

        fun createCompanyCustomerWithOrderer(): CustomerWithContacts {
            val customer = createCompanyCustomer()
            val contact = createContact(orderer = true)
            return CustomerWithContacts(customer, listOf(contact))
        }

        fun createCompanyInvoicingCustomer(
            name: String = "DNA",
            registryKey: String = "3766028-0",
            ovt: String = "003737660280",
        ): InvoicingCustomer =
            InvoicingCustomer(
                type = CustomerType.COMPANY,
                name = name,
                postalAddress = createPostalAddress(),
                email = "info@dna.test",
                phone = "+3581012345678",
                registryKey = registryKey,
                ovt = ovt,
                invoicingOperator = "003721291126",
            )

        fun createPersonInvoicingCustomer(
            name: String = "Liisa Laskutettava",
        ): InvoicingCustomer =
            InvoicingCustomer(
                type = CustomerType.PERSON,
                name = name,
                postalAddress = createPostalAddress(),
                email = "liisa@laskutus.info",
                phone = "963852741",
                registryKey = null,
                ovt = null,
                invoicingOperator = null,
            )

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

        fun createCableReportApplicationArea(
            name: String = "Alue",
            geometry: Polygon = GeometriaFactory.secondPolygon,
        ): CableReportApplicationArea = CableReportApplicationArea(name, geometry)

        fun createExcavationNotificationArea(
            name: String = "Alue",
            hankealueId: Int = 0,
            tyoalueet: List<Tyoalue> = listOf(createTyoalue()),
            katuosoite: String = "Katu 1",
            tyonTarkoitukset: Set<TyomaaTyyppi> = setOf(TyomaaTyyppi.VESI),
            meluhaitta: Meluhaitta = Meluhaitta.JATKUVA_MELUHAITTA,
            polyhaitta: Polyhaitta = Polyhaitta.TOISTUVA_POLYHAITTA,
            tarinahaitta: Tarinahaitta = Tarinahaitta.SATUNNAINEN_TARINAHAITTA,
            kaistahaitta: VaikutusAutoliikenteenKaistamaariin =
                VaikutusAutoliikenteenKaistamaariin.VAHENTAA_KAISTAN_YHDELLA_AJOSUUNNALLA,
            kaistahaittojenPituus: AutoliikenteenKaistavaikutustenPituus =
                AutoliikenteenKaistavaikutustenPituus.PITUUS_ALLE_10_METRIA,
            lisatiedot: String = "Lisätiedot",
        ): ExcavationNotificationArea =
            ExcavationNotificationArea(
                name,
                hankealueId,
                tyoalueet,
                katuosoite,
                tyonTarkoitukset,
                meluhaitta,
                polyhaitta,
                tarinahaitta,
                kaistahaitta,
                kaistahaittojenPituus,
                lisatiedot,
            )

        fun createTyoalue(
            geometry: Polygon = GeometriaFactory.secondPolygon,
            area: Double = 100.0,
            tormaystarkasteluTulos: TormaystarkasteluTulos =
                TormaystarkasteluTulos(1.0f, 3.0f, 5.0f, 5.0f),
        ) = Tyoalue(geometry, area, tormaystarkasteluTulos)

        private fun createApplicationData(applicationType: ApplicationType): ApplicationData =
            when (applicationType) {
                ApplicationType.CABLE_REPORT -> createCableReportApplicationData()
                ApplicationType.EXCAVATION_NOTIFICATION -> createExcavationNotificationData()
            }

        fun createBlankApplicationData(applicationType: ApplicationType): ApplicationData =
            when (applicationType) {
                ApplicationType.CABLE_REPORT -> createBlankCableReportApplicationData()
                ApplicationType.EXCAVATION_NOTIFICATION -> createBlankExcavationNotificationData()
            }

        fun createCableReportApplicationData(
            name: String = DEFAULT_APPLICATION_NAME,
            areas: List<CableReportApplicationArea>? = listOf(createCableReportApplicationArea()),
            startTime: ZonedDateTime? = DateFactory.getStartDatetime(),
            endTime: ZonedDateTime? = DateFactory.getEndDatetime(),
            pendingOnClient: Boolean = false,
            workDescription: String = DEFAULT_WORK_DESCRIPTION,
            customerWithContacts: CustomerWithContacts? =
                createCompanyCustomer().withContacts(createContact(orderer = true)),
            contractorWithContacts: CustomerWithContacts? =
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

        internal fun createBlankCableReportApplicationData() =
            createCableReportApplicationData(
                name = "",
                areas = null,
                startTime = null,
                endTime = null,
                pendingOnClient = false,
                workDescription = "",
                customerWithContacts = null,
                contractorWithContacts = null,
                representativeWithContacts = null,
                propertyDeveloperWithContacts = null,
                rockExcavation = false,
                postalAddress = PostalAddress(StreetAddress(""), "", "")
            )

        fun createExcavationNotificationData(
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
            areas: List<ExcavationNotificationArea>? = listOf(createExcavationNotificationArea()),
            startTime: ZonedDateTime? = DateFactory.getStartDatetime(),
            endTime: ZonedDateTime? = DateFactory.getEndDatetime(),
            customerWithContacts: CustomerWithContacts? =
                createCompanyCustomer().withContacts(createContact(orderer = true)),
            contractorWithContacts: CustomerWithContacts? =
                createCompanyCustomer().withContacts(createContact()),
            representativeWithContacts: CustomerWithContacts? = null,
            propertyDeveloperWithContacts: CustomerWithContacts? = null,
            invoicingCustomer: InvoicingCustomer? = createCompanyInvoicingCustomer(),
            customerReference: String? = "Asiakkaan viite",
            additionalInfo: String? = null,
        ): ExcavationNotificationData =
            ExcavationNotificationData(
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

        internal fun createBlankExcavationNotificationData() =
            createExcavationNotificationData(
                name = "",
                workDescription = "",
                areas = null,
                startTime = null,
                endTime = null,
                customerWithContacts =
                    CustomerWithContacts(Customer(null, "", null, null, null), listOf()),
                contractorWithContacts =
                    CustomerWithContacts(Customer(null, "", null, null, null), listOf()),
                additionalInfo = null
            )

        fun createApplication(
            id: Long = 1,
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
                .map { application -> mapper(application.id, application) }

        fun createApplicationEntity(
            id: Long = 3,
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
                id = 0,
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
}
