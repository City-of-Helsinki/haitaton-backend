package fi.hel.haitaton.hanke.hakemus

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import assertk.assertions.prop
import assertk.assertions.single
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.HankealueService
import fi.hel.haitaton.hanke.allu.AlluCableReportApplicationData
import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.allu.AlluLoginException
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.Contact
import fi.hel.haitaton.hanke.allu.Customer
import fi.hel.haitaton.hanke.allu.CustomerWithContacts
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentService
import fi.hel.haitaton.hanke.attachment.taydennys.TaydennysAttachmentMetadataService
import fi.hel.haitaton.hanke.factory.AlluFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteyshenkiloFactory.withYhteyshenkilo
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.PermissionFactory
import fi.hel.haitaton.hanke.geometria.GeometriatDao
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.logging.HakemusLoggingService
import fi.hel.haitaton.hanke.logging.HankeLoggingService
import fi.hel.haitaton.hanke.logging.Status
import fi.hel.haitaton.hanke.paatos.PaatosService
import fi.hel.haitaton.hanke.pdf.HaittojenhallintasuunnitelmaPdfEncoder
import fi.hel.haitaton.hanke.pdf.withName
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.taydennys.TaydennysRepository
import fi.hel.haitaton.hanke.taydennys.TaydennyspyyntoRepository
import fi.hel.haitaton.hanke.test.AlluException
import fi.hel.haitaton.hanke.test.USERNAME
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluLaskentaService
import fi.hel.haitaton.hanke.valmistumisilmoitus.ValmistumisilmoitusType
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verifySequence
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.stream.Stream
import org.geojson.Polygon
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.context.ApplicationEventPublisher

class HakemusServiceTest {
    private val hakemusRepository: HakemusRepository = mockk()
    private val hankeRepository: HankeRepository = mockk()
    private val taydennysRepository: TaydennysRepository = mockk()
    private val taydennyspyyntoRepository: TaydennyspyyntoRepository = mockk()
    private val geometriatDao: GeometriatDao = mockk()
    private val hankealueService: HankealueService = mockk()
    private val loggingService: HakemusLoggingService = mockk(relaxUnitFun = true)
    private val hankeLoggingService: HankeLoggingService = mockk(relaxUnitFun = true)
    private val disclosureLogService: DisclosureLogService = mockk(relaxUnitFun = true)
    private val hankeKayttajaService: HankeKayttajaService = mockk(relaxUnitFun = true)
    private val attachmentService: ApplicationAttachmentService = mockk()
    private val taydennysAttachmentService: TaydennysAttachmentMetadataService = mockk()
    private val alluClient: AlluClient = mockk()
    private val paatosService: PaatosService = mockk()
    private val publisher: ApplicationEventPublisher = mockk()
    private val tormaystarkasteluLaskentaService: TormaystarkasteluLaskentaService = mockk()
    private val haittojenhallintasuunnitelmaPdfEncoder: HaittojenhallintasuunnitelmaPdfEncoder =
        mockk()

    private val hakemusService =
        HakemusService(
            hakemusRepository,
            hankeRepository,
            taydennyspyyntoRepository,
            taydennysRepository,
            geometriatDao,
            hankealueService,
            loggingService,
            hankeLoggingService,
            disclosureLogService,
            hankeKayttajaService,
            attachmentService,
            taydennysAttachmentService,
            alluClient,
            paatosService,
            publisher,
            tormaystarkasteluLaskentaService,
            haittojenhallintasuunnitelmaPdfEncoder,
        )

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(
            hakemusRepository,
            hankeRepository,
            geometriatDao,
            hankealueService,
            loggingService,
            hankeLoggingService,
            disclosureLogService,
            hankeKayttajaService,
            attachmentService,
            taydennysAttachmentService,
            alluClient,
            paatosService,
            publisher,
            haittojenhallintasuunnitelmaPdfEncoder,
        )
    }

    @Nested
    inner class SendApplication {
        private val hankeTunnus = HankeFactory.defaultHankeTunnus
        private val alluId = 42

        private fun callRealWrapper() {
            every { disclosureLogService.withDisclosureLogging<Int>(any(), any(), any()) } answers
                {
                    callOriginal()
                }
        }

        @Test
        fun `save disclosure logs when sending succeeds`() {
            val applicationEntity = applicationEntity()
            val hakija = applicationEntity.yhteystiedot[ApplicationContactType.HAKIJA]!!
            val suorittaja =
                applicationEntity.yhteystiedot[ApplicationContactType.TYON_SUORITTAJA]!!
            every { hakemusRepository.findOneById(3) } returns applicationEntity
            every { hakemusRepository.save(any()) } answers { firstArg() }
            every { alluClient.create(any()) } returns alluId
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse()
            every { geometriatDao.isInsideHankeAlueet(1, any()) } returns true
            every { geometriatDao.calculateCombinedArea(any()) } returns 11.0f
            every { geometriatDao.calculateArea(any()) } returns 11.0f
            every { attachmentService.getMetadataList(applicationEntity.id) } returns listOf()
            justRun { alluClient.addAttachment(alluId, any()) }
            justRun { attachmentService.sendInitialAttachments(alluId, any()) }
            callRealWrapper()
            val applicationCapturingSlot = slot<AlluCableReportApplicationData>()
            justRun {
                disclosureLogService.saveForAllu(
                    3,
                    capture(applicationCapturingSlot),
                    Status.SUCCESS,
                )
            }

            hakemusService.sendHakemus(3, null, USERNAME)

            val sent = applicationCapturingSlot.captured
            assertThat(sent.identificationNumber).isEqualTo(hankeTunnus)
            assertThat(sent.pendingOnClient).isFalse()
            assertThat(sent.name).isEqualTo(applicationData.name)
            assertThat(sent.postalAddress).isNull()
            assertThat(sent.constructionWork).isFalse()
            assertThat(sent.maintenanceWork).isFalse()
            assertThat(sent.propertyConnectivity).isFalse()
            assertThat(sent.emergencyWork).isFalse()
            val expectedDescription = applicationData.workDescription + "\nEi louhita"
            assertThat(sent.workDescription).isEqualTo(expectedDescription)
            assertThat(sent.clientApplicationKind).isEqualTo(expectedDescription)
            assertThat(sent.startTime).isEqualTo(applicationData.startTime)
            assertThat(sent.endTime).isEqualTo(applicationData.endTime)
            assertThat(sent.pendingOnClient).isFalse()
            val expectedGeometry =
                Polygon().apply {
                    crs = null
                    coordinates = applicationData.areas!![0].geometry.coordinates
                }
            assertThat(sent.geometry.geometries).single().isEqualTo(expectedGeometry)
            assertThat(sent.area).isNull()
            val hakijaYhteyshenkilo = hakija.yhteyshenkilot.single().hankekayttaja
            assertThat(sent.customerWithContacts).all {
                prop(CustomerWithContacts::customer).all {
                    prop(Customer::type).isEqualTo(hakija.tyyppi)
                    prop(Customer::name).isEqualTo(hakija.nimi)
                    prop(Customer::postalAddress).isNull()
                    prop(Customer::email).isEqualTo(hakija.sahkoposti)
                    prop(Customer::phone).isEqualTo(hakija.puhelinnumero)
                    prop(Customer::registryKey).isEqualTo(hakija.registryKey)
                    prop(Customer::ovt).isNull()
                    prop(Customer::invoicingOperator).isNull()
                    prop(Customer::country).isEqualTo("FI")
                    prop(Customer::sapCustomerNumber).isNull()
                }
                prop(CustomerWithContacts::contacts).single().all {
                    prop(Contact::name).isEqualTo(hakijaYhteyshenkilo.fullName())
                    prop(Contact::email).isEqualTo(hakijaYhteyshenkilo.sahkoposti)
                    prop(Contact::phone).isEqualTo(hakijaYhteyshenkilo.puhelin)
                    prop(Contact::orderer).isTrue()
                }
            }
            val suorittajaYhteyshenkilo = suorittaja.yhteyshenkilot.single().hankekayttaja
            assertThat(sent.contractorWithContacts).all {
                prop(CustomerWithContacts::customer).all {
                    prop(Customer::type).isEqualTo(suorittaja.tyyppi)
                    prop(Customer::name).isEqualTo(suorittaja.nimi)
                    prop(Customer::postalAddress).isNull()
                    prop(Customer::email).isEqualTo(suorittaja.sahkoposti)
                    prop(Customer::phone).isEqualTo(suorittaja.puhelinnumero)
                    prop(Customer::registryKey).isEqualTo(suorittaja.registryKey)
                    prop(Customer::ovt).isNull()
                    prop(Customer::invoicingOperator).isNull()
                    prop(Customer::country).isEqualTo("FI")
                    prop(Customer::sapCustomerNumber).isNull()
                }
                prop(CustomerWithContacts::contacts).single().all {
                    prop(Contact::name).isEqualTo(suorittajaYhteyshenkilo.fullName())
                    prop(Contact::email).isEqualTo(suorittajaYhteyshenkilo.sahkoposti)
                    prop(Contact::phone).isEqualTo(suorittajaYhteyshenkilo.puhelin)
                    prop(Contact::orderer).isFalse()
                }
            }
            assertThat(sent.propertyDeveloperWithContacts).isNull()
            assertThat(sent.representativeWithContacts).isNull()
            assertThat(sent.invoicingCustomer).isNull()
            assertThat(sent.customerReference).isNull()
            assertThat(sent.trafficArrangementImages).isNull()

            verifySequence {
                hakemusRepository.findOneById(3)
                geometriatDao.isInsideHankeAlueet(1, any())
                attachmentService.getMetadataList(applicationEntity.id)
                geometriatDao.calculateCombinedArea(any())
                geometriatDao.calculateArea(any())
                disclosureLogService.withDisclosureLogging<Int>(applicationEntity.id, any(), any())
                alluClient.create(any())
                disclosureLogService.saveForAllu(3, any(), Status.SUCCESS)
                alluClient.addAttachment(alluId, withName(FORM_DATA_PDF_FILENAME))
                attachmentService.sendInitialAttachments(alluId, any())
                alluClient.getApplicationInformation(alluId)
                hakemusRepository.save(any())
            }
        }

        @Test
        fun `saves disclosure logs when sending fails`() {
            val applicationEntity = applicationEntity()
            every { hakemusRepository.findOneById(3) } returns applicationEntity
            every { geometriatDao.calculateArea(any()) } returns 11.0f
            every { attachmentService.getMetadataList(applicationEntity.id) } returns listOf()
            every { geometriatDao.calculateCombinedArea(any()) } returns 11.0f
            every { alluClient.create(any()) } throws AlluException()
            every { geometriatDao.isInsideHankeAlueet(1, any()) } returns true
            callRealWrapper()

            assertThrows<AlluException> { hakemusService.sendHakemus(3, null, USERNAME) }

            verifySequence {
                hakemusRepository.findOneById(3)
                geometriatDao.isInsideHankeAlueet(1, any())
                attachmentService.getMetadataList(applicationEntity.id)
                geometriatDao.calculateCombinedArea(any())
                geometriatDao.calculateArea(any())
                disclosureLogService.withDisclosureLogging<Int>(applicationEntity.id, any(), any())
                alluClient.create(any())
                disclosureLogService.saveForAllu(
                    3,
                    any(),
                    Status.FAILED,
                    ALLU_APPLICATION_ERROR_MSG,
                )
            }
        }

        @Test
        fun `does not save disclosure logs when allu login fails`() {
            val applicationEntity = applicationEntity()
            every { hakemusRepository.findOneById(3) } returns applicationEntity
            every { geometriatDao.isInsideHankeAlueet(any(), any()) } returns true
            every { geometriatDao.calculateCombinedArea(any()) } returns 11.0f
            every { geometriatDao.calculateArea(any()) } returns 11.0f
            every { attachmentService.getMetadataList(applicationEntity.id) } returns listOf()
            every { alluClient.create(any()) } throws AlluLoginException(RuntimeException())
            callRealWrapper()

            assertThrows<AlluLoginException> { hakemusService.sendHakemus(3, null, USERNAME) }

            verifySequence {
                hakemusRepository.findOneById(3)
                geometriatDao.isInsideHankeAlueet(any(), any())
                attachmentService.getMetadataList(applicationEntity.id)
                geometriatDao.calculateCombinedArea(any())
                geometriatDao.calculateArea(any())
                disclosureLogService.withDisclosureLogging<Int>(applicationEntity.id, any(), any())
                alluClient.create(any())
            }
        }

        @ParameterizedTest
        @CsvSource("true,Louhitaan", "false,Ei louhita")
        fun `adds rock excavation information to work description`(
            rockExcavation: Boolean,
            expectedSuffix: String,
        ) {
            val applicationEntity = applicationEntity()
            applicationEntity.hakemusEntityData =
                (applicationEntity.hakemusEntityData as JohtoselvityshakemusEntityData).copy(
                    rockExcavation = rockExcavation
                )
            every { hakemusRepository.findOneById(3) } returns applicationEntity
            every { hakemusRepository.save(any()) } answers { firstArg() }
            every { geometriatDao.isInsideHankeAlueet(1, any()) } returns true
            every { geometriatDao.calculateCombinedArea(any()) } returns 11.0f
            every { geometriatDao.calculateArea(any()) } returns 11.0f
            every { attachmentService.getMetadataList(applicationEntity.id) } returns listOf()
            val applicationCapturingSlot = slot<AlluCableReportApplicationData>()
            every { alluClient.create(capture(applicationCapturingSlot)) } returns alluId
            justRun { alluClient.addAttachment(alluId, any()) }
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse(alluId)
            justRun { attachmentService.sendInitialAttachments(alluId, any()) }
            callRealWrapper()

            hakemusService.sendHakemus(3, null, USERNAME)

            val sent = applicationCapturingSlot.captured
            val expectedDescription = applicationData.workDescription + "\n" + expectedSuffix
            assertThat(sent.workDescription).isEqualTo(expectedDescription)
            assertThat(sent.clientApplicationKind).isEqualTo(expectedDescription)
            verifySequence {
                hakemusRepository.findOneById(3)
                geometriatDao.isInsideHankeAlueet(1, any())
                attachmentService.getMetadataList(applicationEntity.id)
                geometriatDao.calculateCombinedArea(any())
                geometriatDao.calculateArea(any())
                disclosureLogService.withDisclosureLogging<Int>(applicationEntity.id, any(), any())
                alluClient.create(any())
                disclosureLogService.saveForAllu(3, any(), Status.SUCCESS)
                alluClient.addAttachment(alluId, withName(FORM_DATA_PDF_FILENAME))
                attachmentService.sendInitialAttachments(alluId, any())
                alluClient.getApplicationInformation(alluId)
                hakemusRepository.save(any())
            }
        }

        @ParameterizedTest(name = "{1}")
        @MethodSource("fi.hel.haitaton.hanke.hakemus.HakemusServiceTest#invalidData")
        fun `throws exception when application has invalid data`(
            hakemusEntityData: HakemusEntityData,
            path: String,
        ) {
            val applicationEntity = applicationEntity()
            applicationEntity.hakemusEntityData = hakemusEntityData
            every { hakemusRepository.findOneById(3) } returns applicationEntity

            assertFailure { hakemusService.sendHakemus(3, null, USERNAME) }
                .all {
                    hasClass(InvalidHakemusDataException::class)
                    hasMessage("Application contains invalid data. Errors at paths: $path")
                }

            verifySequence { hakemusRepository.findOneById(3) }
        }

        @Test
        fun `throws exception when application has invalid customer`() {
            val applicationEntity = applicationEntity()
            applicationEntity.yhteystiedot[ApplicationContactType.HAKIJA]!!.nimi = ""
            every { hakemusRepository.findOneById(3) } returns applicationEntity

            assertFailure { hakemusService.sendHakemus(3, null, USERNAME) }
                .all {
                    hasClass(InvalidHakemusDataException::class)
                    hasMessage(
                        "Application contains invalid data. Errors at paths: applicationData.customerWithContacts.nimi"
                    )
                }

            verifySequence { hakemusRepository.findOneById(3) }
        }

        @Test
        fun `throws exception when application has invalid contractor`() {
            val applicationEntity = applicationEntity()
            val yhteystieto = applicationEntity.yhteystiedot[ApplicationContactType.TYON_SUORITTAJA]
            val hankekayttaja = yhteystieto!!.yhteyshenkilot[0].hankekayttaja
            hankekayttaja.etunimi = ""
            hankekayttaja.sukunimi = ""
            every { hakemusRepository.findOneById(3) } returns applicationEntity

            assertFailure { hakemusService.sendHakemus(3, null, USERNAME) }
                .all {
                    hasClass(InvalidHakemusDataException::class)
                    hasMessage(
                        "Application contains invalid data. Errors at paths: " +
                            "applicationData.contractorWithContacts.yhteyshenkilot[0].etunimi"
                    )
                }

            verifySequence { hakemusRepository.findOneById(3) }
        }

        @Test
        fun `throws exception when application has invalid property developer`() {
            val applicationEntity = applicationEntity()
            applicationEntity.yhteystiedot[ApplicationContactType.RAKENNUTTAJA] =
                HakemusyhteystietoFactory.createEntity(
                        application = applicationEntity,
                        sahkoposti = "  ",
                    )
                    .withYhteyshenkilo(
                        permission = PermissionFactory.createEntity(userId = USERNAME)
                    )
            every { hakemusRepository.findOneById(3) } returns applicationEntity

            assertFailure { hakemusService.sendHakemus(3, null, USERNAME) }
                .all {
                    hasClass(InvalidHakemusDataException::class)
                    hasMessage(
                        "Application contains invalid data. Errors at paths: " +
                            "applicationData.propertyDeveloperWithContacts.sahkoposti"
                    )
                }

            verifySequence { hakemusRepository.findOneById(3) }
        }

        @Test
        fun `throws exception when application has invalid representative`() {
            val applicationEntity = applicationEntity()
            applicationEntity.yhteystiedot[ApplicationContactType.ASIANHOITAJA] =
                HakemusyhteystietoFactory.createEntity(
                        application = applicationEntity,
                        puhelinnumero = "  ",
                    )
                    .withYhteyshenkilo(
                        permission = PermissionFactory.createEntity(userId = USERNAME)
                    )
            every { hakemusRepository.findOneById(3) } returns applicationEntity

            assertFailure { hakemusService.sendHakemus(3, null, USERNAME) }
                .all {
                    hasClass(InvalidHakemusDataException::class)
                    hasMessage(
                        "Application contains invalid data. Errors at paths: " +
                            "applicationData.representativeWithContacts.puhelinnumero"
                    )
                }

            verifySequence { hakemusRepository.findOneById(3) }
        }

        private fun applicationEntity(): HakemusEntity {
            val hankeEntity = HankeFactory.createMinimalEntity(id = 1)
            val applicationEntity =
                ApplicationFactory.createApplicationEntity(
                    userId = USERNAME,
                    hakemusEntityData = applicationData,
                    hanke = hankeEntity,
                )
            applicationEntity.yhteystiedot[ApplicationContactType.HAKIJA] =
                HakemusyhteystietoFactory.createEntity(application = applicationEntity)
                    .withYhteyshenkilo(
                        permission = PermissionFactory.createEntity(userId = USERNAME)
                    )
            applicationEntity.yhteystiedot[ApplicationContactType.TYON_SUORITTAJA] =
                HakemusyhteystietoFactory.createEntity(application = applicationEntity)
                    .withYhteyshenkilo(
                        permission = PermissionFactory.createEntity(userId = USERNAME)
                    )
            return applicationEntity
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class ReportCompletionDate {
        private val today: LocalDate = LocalDate.now()
        private val tomorrow: LocalDate = today.plusDays(1)
        private val startDate: ZonedDateTime = ZonedDateTime.parse("2024-08-08T00:00Z")
        private val beforeStart: LocalDate = LocalDate.parse("2024-08-07")
        private val endDate: ZonedDateTime = ZonedDateTime.parse("2024-08-12T00:00Z")

        private val id = 16984L
        private val alluId = 4141
        private val applicationIdentifier = "JS2400014"
        private val hanke = HankeFactory.createEntity()
        private val hakemusData =
            ApplicationFactory.createExcavationNotificationData(
                startTime = startDate,
                endTime = endDate,
            )
        private val hakemus =
            HakemusFactory.createEntity(
                id = id,
                alluid = alluId,
                alluStatus = ApplicationStatus.PENDING,
                applicationIdentifier = applicationIdentifier,
                userId = USERNAME,
                applicationType = ApplicationType.EXCAVATION_NOTIFICATION,
                hakemusEntityData = hakemusData,
                hanke = hanke,
            )

        private fun allowedDateParams() =
            listOf(
                    "2024-08-08", // Start date
                    "2024-08-09", // After start date
                    "2024-08-12", // End date
                    "2024-08-13", // After end date
                )
                .map(LocalDate::parse)
                .flatMap { date -> ValmistumisilmoitusType.entries.map { Arguments.of(it, date) } }

        @ParameterizedTest
        @MethodSource("allowedDateParams")
        fun `sends the date to Allu when the date is between the hakemus start date and today`(
            ilmoitusType: ValmistumisilmoitusType,
            date: LocalDate,
        ) {
            every { hakemusRepository.findOneById(id) } returns hakemus
            justRun { alluClient.reportCompletionDate(ilmoitusType, alluId, date) }

            hakemusService.reportCompletionDate(ilmoitusType, hakemus.id, date)

            verifySequence {
                hakemusRepository.findOneById(id)
                alluClient.reportCompletionDate(ilmoitusType, alluId, date)
            }
        }

        @ParameterizedTest
        @EnumSource(ValmistumisilmoitusType::class)
        fun `throws exception when date is before the start date of the application`(
            ilmoitusType: ValmistumisilmoitusType
        ) {
            every { hakemusRepository.findOneById(id) } returns hakemus

            val failure = assertFailure {
                hakemusService.reportCompletionDate(ilmoitusType, id, beforeStart)
            }

            failure.all {
                hasClass(CompletionDateException::class)
                messageContains("Invalid date in ${ilmoitusType.logName} report")
                messageContains("Date is before the hakemus start date")
                messageContains("start date: 2024-08-08T00:00")
                messageContains("date=2024-08-07")
                messageContains("id=${hakemus.id}")
            }
            verifySequence { hakemusRepository.findOneById(id) }
        }

        @ParameterizedTest
        @EnumSource(ValmistumisilmoitusType::class)
        fun `throws exception when date is in the future`(ilmoitusType: ValmistumisilmoitusType) {
            every { hakemusRepository.findOneById(id) } returns
                hakemus.copy(
                    hakemusEntityData =
                        hakemusData.copy(
                            startTime = ZonedDateTime.now().minusDays(5),
                            endTime = ZonedDateTime.now().plusDays(5),
                        )
                )

            val failure = assertFailure {
                hakemusService.reportCompletionDate(ilmoitusType, hakemus.id, tomorrow)
            }

            failure.all {
                hasClass(CompletionDateException::class)
                messageContains("Invalid date in ${ilmoitusType.logName} report")
                messageContains("Date is in the future")
                messageContains("date=$tomorrow")
                messageContains("id=${hakemus.id}")
            }
            verifySequence { hakemusRepository.findOneById(id) }
        }

        private val allowedStatusesForToiminnallinenKunto =
            setOf(
                ApplicationStatus.PENDING,
                ApplicationStatus.HANDLING,
                ApplicationStatus.INFORMATION_RECEIVED,
                ApplicationStatus.RETURNED_TO_PREPARATION,
                ApplicationStatus.DECISIONMAKING,
                ApplicationStatus.DECISION,
            )

        private val allowedStatusesForTyoValmis =
            allowedStatusesForToiminnallinenKunto + ApplicationStatus.OPERATIONAL_CONDITION

        private fun allowedStatusParams(): List<Arguments> {
            val forToiminnallinenKunto =
                allowedStatusesForToiminnallinenKunto.map {
                    Arguments.of(ValmistumisilmoitusType.TOIMINNALLINEN_KUNTO, it)
                }
            val forTyoValmis =
                allowedStatusesForTyoValmis.map {
                    Arguments.of(ValmistumisilmoitusType.TYO_VALMIS, it)
                }
            return forToiminnallinenKunto + forTyoValmis
        }

        private fun rejectedStatusParams(
            ilmoitusType: ValmistumisilmoitusType,
            allowed: Set<ApplicationStatus>,
        ): List<Arguments> {
            val rejected =
                ApplicationStatus.entries.minus(allowed).map { Arguments.of(ilmoitusType, it) }
            return rejected + Arguments.of(ilmoitusType, null)
        }

        private fun rejectedStatusParams(): List<Arguments> {
            val forToiminnallinenKunto =
                rejectedStatusParams(
                    ValmistumisilmoitusType.TOIMINNALLINEN_KUNTO,
                    allowedStatusesForToiminnallinenKunto,
                )
            val forTyoValmis =
                rejectedStatusParams(
                    ValmistumisilmoitusType.TYO_VALMIS,
                    allowedStatusesForTyoValmis,
                )
            return forToiminnallinenKunto + forTyoValmis
        }

        @ParameterizedTest
        @MethodSource("allowedStatusParams")
        fun `sends the date to Allu when the date is today and the status is allowed`(
            ilmoitusType: ValmistumisilmoitusType,
            status: ApplicationStatus,
        ) {
            every { hakemusRepository.findOneById(id) } returns hakemus.copy(alluStatus = status)
            justRun { alluClient.reportCompletionDate(ilmoitusType, alluId, today) }

            hakemusService.reportCompletionDate(ilmoitusType, hakemus.id, today)

            verifySequence {
                hakemusRepository.findOneById(id)
                alluClient.reportCompletionDate(ilmoitusType, hakemus.alluid!!, today)
            }
        }

        @ParameterizedTest
        @MethodSource("rejectedStatusParams")
        fun `throws exception when application status is not allowed`(
            ilmoitusType: ValmistumisilmoitusType,
            status: ApplicationStatus?,
        ) {
            every { hakemusRepository.findOneById(id) } returns hakemus.copy(alluStatus = status)

            val failure = assertFailure {
                hakemusService.reportCompletionDate(ilmoitusType, hakemus.id, today)
            }

            failure.all {
                hasClass(HakemusInWrongStatusException::class)
                messageContains("Hakemus is in the wrong status for this operation")
                messageContains("status=${status?.name}")
                messageContains(
                    "allowed statuses=PENDING, HANDLING, INFORMATION_RECEIVED, RETURNED_TO_PREPARATION, DECISIONMAKING, DECISION"
                )
            }
            verifySequence { hakemusRepository.findOneById(id) }
        }
    }

    companion object {
        private val applicationData: JohtoselvityshakemusEntityData =
            ApplicationFactory.createCableReportApplicationData()

        @JvmStatic
        private fun invalidData(): Stream<Arguments> =
            Stream.of(
                Arguments.of(applicationData.copy(endTime = null), "applicationData.endTime"),
                Arguments.of(applicationData.copy(startTime = null), "applicationData.startTime"),
                Arguments.of(
                    applicationData.copy(rockExcavation = null),
                    "applicationData.rockExcavation",
                ),
            )
    }
}
