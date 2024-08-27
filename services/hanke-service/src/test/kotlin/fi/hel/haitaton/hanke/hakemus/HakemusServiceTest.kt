package fi.hel.haitaton.hanke.hakemus

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
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
import fi.hel.haitaton.hanke.allu.AlluStatus
import fi.hel.haitaton.hanke.allu.AlluStatusRepository
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.Contact
import fi.hel.haitaton.hanke.allu.Customer
import fi.hel.haitaton.hanke.allu.CustomerWithContacts
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentService
import fi.hel.haitaton.hanke.email.EmailSenderService
import fi.hel.haitaton.hanke.email.JohtoselvitysCompleteEmail
import fi.hel.haitaton.hanke.factory.AlluFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteyshenkiloFactory
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
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.test.AlluException
import fi.hel.haitaton.hanke.test.USERNAME
import io.mockk.called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifySequence
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.util.stream.Stream
import org.geojson.Polygon
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.ApplicationEventPublisher

class HakemusServiceTest {
    private val hakemusRepository: HakemusRepository = mockk()
    private val hankeRepository: HankeRepository = mockk()
    private val geometriatDao: GeometriatDao = mockk()
    private val hankealueService: HankealueService = mockk()
    private val loggingService: HakemusLoggingService = mockk(relaxUnitFun = true)
    private val hankeLoggingService: HankeLoggingService = mockk(relaxUnitFun = true)
    private val disclosureLogService: DisclosureLogService = mockk(relaxUnitFun = true)
    private val hankeKayttajaService: HankeKayttajaService = mockk(relaxUnitFun = true)
    private val attachmentService: ApplicationAttachmentService = mockk()
    private val alluClient: AlluClient = mockk()
    private val alluStatusRepository: AlluStatusRepository = mockk()
    private val emailSenderService: EmailSenderService = mockk()
    private val paatosService: PaatosService = mockk()
    private val publisher: ApplicationEventPublisher = mockk()

    private val hakemusService =
        HakemusService(
            hakemusRepository,
            hankeRepository,
            geometriatDao,
            hankealueService,
            loggingService,
            hankeLoggingService,
            disclosureLogService,
            hankeKayttajaService,
            attachmentService,
            alluClient,
            alluStatusRepository,
            emailSenderService,
            paatosService,
            publisher,
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
            disclosureLogService,
            hankeKayttajaService,
            attachmentService,
            alluClient,
        )
    }

    @Nested
    inner class SendApplication {
        private val hankeTunnus = HankeFactory.defaultHankeTunnus
        private val alluId = 42

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
            val applicationCapturingSlot = slot<AlluCableReportApplicationData>()
            justRun {
                disclosureLogService.saveDisclosureLogsForAllu(
                    3, capture(applicationCapturingSlot), Status.SUCCESS)
            }

            hakemusService.sendHakemus(3, USERNAME)

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
                    prop(Customer::registryKey).isEqualTo(hakija.ytunnus)
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
                    prop(Customer::registryKey).isEqualTo(suorittaja.ytunnus)
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
                geometriatDao.calculateCombinedArea(any())
                attachmentService.getMetadataList(applicationEntity.id)
                geometriatDao.calculateArea(any())
                alluClient.create(any())
                disclosureLogService.saveDisclosureLogsForAllu(3, any(), Status.SUCCESS)
                alluClient.addAttachment(alluId, any())
                attachmentService.sendInitialAttachments(alluId, any())
                alluClient.getApplicationInformation(alluId)
                hakemusRepository.save(any())
            }
        }

        @Test
        fun `saves disclosure logs when sending fails`() {
            val applicationEntity = applicationEntity()
            every { hakemusRepository.findOneById(3) } returns applicationEntity
            every { geometriatDao.calculateCombinedArea(any()) } returns 11.0f
            every { geometriatDao.calculateArea(any()) } returns 11.0f
            every { attachmentService.getMetadataList(applicationEntity.id) } returns listOf()
            every { alluClient.create(any()) } throws AlluException()
            every { geometriatDao.isInsideHankeAlueet(1, any()) } returns true

            assertThrows<AlluException> { hakemusService.sendHakemus(3, USERNAME) }

            verifySequence {
                hakemusRepository.findOneById(3)
                geometriatDao.isInsideHankeAlueet(1, any())
                geometriatDao.calculateCombinedArea(any())
                attachmentService.getMetadataList(applicationEntity.id)
                geometriatDao.calculateArea(any())
                alluClient.create(any())
                disclosureLogService.saveDisclosureLogsForAllu(
                    3, any(), Status.FAILED, ALLU_APPLICATION_ERROR_MSG)
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

            assertThrows<AlluLoginException> { hakemusService.sendHakemus(3, USERNAME) }

            verifySequence {
                hakemusRepository.findOneById(3)
                geometriatDao.isInsideHankeAlueet(any(), any())
                geometriatDao.calculateCombinedArea(any())
                attachmentService.getMetadataList(applicationEntity.id)
                geometriatDao.calculateArea(any())
                alluClient.create(any())
            }
            verify { disclosureLogService wasNot called }
        }

        @ParameterizedTest
        @CsvSource("true,Louhitaan", "false,Ei louhita")
        fun `adds rock excavation information to work description`(
            rockExcavation: Boolean,
            expectedSuffix: String
        ) {
            val applicationEntity = applicationEntity()
            applicationEntity.hakemusEntityData =
                (applicationEntity.hakemusEntityData as JohtoselvityshakemusEntityData).copy(
                    rockExcavation = rockExcavation)
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

            hakemusService.sendHakemus(3, USERNAME)

            val sent = applicationCapturingSlot.captured
            val expectedDescription = applicationData.workDescription + "\n" + expectedSuffix
            assertThat(sent.workDescription).isEqualTo(expectedDescription)
            assertThat(sent.clientApplicationKind).isEqualTo(expectedDescription)
            verifySequence {
                hakemusRepository.findOneById(3)
                geometriatDao.isInsideHankeAlueet(1, any())
                geometriatDao.calculateCombinedArea(any())
                attachmentService.getMetadataList(applicationEntity.id)
                geometriatDao.calculateArea(any())
                alluClient.create(any())
                disclosureLogService.saveDisclosureLogsForAllu(3, any(), Status.SUCCESS)
                alluClient.addAttachment(alluId, any())
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

            assertFailure { hakemusService.sendHakemus(3, USERNAME) }
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

            assertFailure { hakemusService.sendHakemus(3, USERNAME) }
                .all {
                    hasClass(InvalidHakemusDataException::class)
                    hasMessage(
                        "Application contains invalid data. Errors at paths: applicationData.customerWithContacts.nimi")
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

            assertFailure { hakemusService.sendHakemus(3, USERNAME) }
                .all {
                    hasClass(InvalidHakemusDataException::class)
                    hasMessage(
                        "Application contains invalid data. Errors at paths: " +
                            "applicationData.contractorWithContacts.yhteyshenkilot[0].etunimi")
                }

            verifySequence { hakemusRepository.findOneById(3) }
        }

        @Test
        fun `throws exception when application has invalid property developer`() {
            val applicationEntity = applicationEntity()
            applicationEntity.yhteystiedot[ApplicationContactType.RAKENNUTTAJA] =
                HakemusyhteystietoFactory.createEntity(
                        application = applicationEntity, sahkoposti = "  ")
                    .withYhteyshenkilo(
                        permission = PermissionFactory.createEntity(userId = USERNAME))
            every { hakemusRepository.findOneById(3) } returns applicationEntity

            assertFailure { hakemusService.sendHakemus(3, USERNAME) }
                .all {
                    hasClass(InvalidHakemusDataException::class)
                    hasMessage(
                        "Application contains invalid data. Errors at paths: " +
                            "applicationData.propertyDeveloperWithContacts.sahkoposti")
                }

            verifySequence { hakemusRepository.findOneById(3) }
        }

        @Test
        fun `throws exception when application has invalid representative`() {
            val applicationEntity = applicationEntity()
            applicationEntity.yhteystiedot[ApplicationContactType.ASIANHOITAJA] =
                HakemusyhteystietoFactory.createEntity(
                        application = applicationEntity, puhelinnumero = "  ")
                    .withYhteyshenkilo(
                        permission = PermissionFactory.createEntity(userId = USERNAME))
            every { hakemusRepository.findOneById(3) } returns applicationEntity

            assertFailure { hakemusService.sendHakemus(3, USERNAME) }
                .all {
                    hasClass(InvalidHakemusDataException::class)
                    hasMessage(
                        "Application contains invalid data. Errors at paths: " +
                            "applicationData.representativeWithContacts.puhelinnumero")
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
                        permission = PermissionFactory.createEntity(userId = USERNAME))
            applicationEntity.yhteystiedot[ApplicationContactType.TYON_SUORITTAJA] =
                HakemusyhteystietoFactory.createEntity(application = applicationEntity)
                    .withYhteyshenkilo(
                        permission = PermissionFactory.createEntity(userId = USERNAME))
            return applicationEntity
        }
    }

    @Nested
    inner class ReportOperationalCondition {
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

        @ParameterizedTest
        @ValueSource(
            strings =
                [
                    "2024-08-08", // Start date
                    "2024-08-09", // After start date
                    "2024-08-12", // End date
                    "2024-08-13", // After end date
                ])
        fun `sends the date to Allu when the date is between the hakemus start date and today`(
            date: LocalDate
        ) {
            every { hakemusRepository.findOneById(id) } returns hakemus
            justRun { alluClient.reportOperationalCondition(alluId, date) }

            hakemusService.reportOperationalCondition(hakemus.id, date)

            verifySequence {
                hakemusRepository.findOneById(id)
                alluClient.reportOperationalCondition(alluId, date)
            }
        }

        @Test
        fun `throws exception when date is before the start date of the application`() {
            every { hakemusRepository.findOneById(id) } returns hakemus

            val failure = assertFailure {
                hakemusService.reportOperationalCondition(id, beforeStart)
            }

            failure.all {
                hasClass(OperationalConditionDateException::class)
                messageContains("Invalid date in operational condition report")
                messageContains("Date is before the hakemus start date")
                messageContains("start date: 2024-08-08T00:00")
                messageContains("date=2024-08-07")
                messageContains("id=${hakemus.id}")
            }
            verifySequence { hakemusRepository.findOneById(id) }
        }

        @Test
        fun `throws exception when date is in the future`() {
            every { hakemusRepository.findOneById(id) } returns
                hakemus.copy(
                    hakemusEntityData =
                        hakemusData.copy(
                            startTime = ZonedDateTime.now().minusDays(5),
                            endTime = ZonedDateTime.now().plusDays(5),
                        ))

            val failure = assertFailure {
                hakemusService.reportOperationalCondition(hakemus.id, tomorrow)
            }

            failure.all {
                hasClass(OperationalConditionDateException::class)
                messageContains("Invalid date in operational condition report")
                messageContains("Date is in the future")
                messageContains("date=$tomorrow")
                messageContains("id=${hakemus.id}")
            }
            verifySequence { hakemusRepository.findOneById(id) }
        }

        @ParameterizedTest
        @EnumSource(
            ApplicationStatus::class,
            names =
                [
                    "PENDING",
                    "HANDLING",
                    "INFORMATION_RECEIVED",
                    "RETURNED_TO_PREPARATION",
                    "DECISIONMAKING",
                    "DECISION",
                ],
        )
        fun `sends the date to Allu when the date is today and the status is allowed`(
            status: ApplicationStatus
        ) {
            every { hakemusRepository.findOneById(id) } returns hakemus.copy(alluStatus = status)
            justRun { alluClient.reportOperationalCondition(alluId, today) }

            hakemusService.reportOperationalCondition(hakemus.id, today)

            verifySequence {
                hakemusRepository.findOneById(id)
                alluClient.reportOperationalCondition(hakemus.alluid!!, today)
            }
        }

        @ParameterizedTest
        @NullSource
        @EnumSource(
            ApplicationStatus::class,
            names =
                [
                    "PENDING",
                    "HANDLING",
                    "INFORMATION_RECEIVED",
                    "RETURNED_TO_PREPARATION",
                    "DECISIONMAKING",
                    "DECISION",
                ],
            mode = EnumSource.Mode.EXCLUDE,
        )
        fun `throws exception when application status is not allowed`(status: ApplicationStatus?) {
            every { hakemusRepository.findOneById(id) } returns hakemus.copy(alluStatus = status)

            val failure = assertFailure {
                hakemusService.reportOperationalCondition(hakemus.id, today)
            }

            failure.all {
                hasClass(HakemusInWrongStatusException::class)
                messageContains("Hakemus is in the wrong status for this operation")
                messageContains("status=${status?.name}")
                messageContains(
                    "allowed statuses=PENDING, HANDLING, INFORMATION_RECEIVED, RETURNED_TO_PREPARATION, DECISIONMAKING, DECISION")
            }
            verifySequence { hakemusRepository.findOneById(id) }
        }
    }

    @Nested
    @ExtendWith(OutputCaptureExtension::class)
    inner class HandleApplicationUpdates {
        private val alluid = 42
        private val applicationId = 13L
        private val hankeTunnus = "HAI23-1"
        private val receiver = HakemusyhteyshenkiloFactory.DEFAULT_SAHKOPOSTI
        private val updateTime = OffsetDateTime.parse("2022-10-09T06:36:51Z")
        private val identifier = ApplicationHistoryFactory.DEFAULT_APPLICATION_IDENTIFIER

        @Test
        fun `sends email to the contacts when hakemus gets a decision`() {
            every { hakemusRepository.getOneByAlluid(42) } returns applicationEntityWithCustomer()
            justRun {
                publisher.publishEvent(
                    JohtoselvitysCompleteEmail(receiver, applicationId, identifier))
            }
            every { hakemusRepository.save(any()) } answers { firstArg() }
            every { alluStatusRepository.getReferenceById(1) } returns AlluStatus(1, updateTime)
            every { alluStatusRepository.save(any()) } answers { firstArg() }

            hakemusService.handleHakemusUpdates(createHistories(), updateTime)

            verifySequence {
                hakemusRepository.getOneByAlluid(42)
                publisher.publishEvent(
                    JohtoselvitysCompleteEmail(receiver, applicationId, identifier))
                hakemusRepository.save(any())
                alluStatusRepository.getReferenceById(1)
                alluStatusRepository.save(any())
            }
        }

        @Test
        fun `doesn't send email when status is not decision`() {
            every { hakemusRepository.getOneByAlluid(42) } returns applicationEntityWithCustomer()
            every { hakemusRepository.save(any()) } answers { firstArg() }
            every { alluStatusRepository.getReferenceById(1) } returns AlluStatus(1, updateTime)
            every { alluStatusRepository.save(any()) } answers { firstArg() }
            val histories =
                listOf(
                    ApplicationHistoryFactory.create(
                        alluid,
                        ApplicationHistoryFactory.createEvent(
                            applicationIdentifier = identifier,
                            newStatus = ApplicationStatus.HANDLING)),
                )

            hakemusService.handleHakemusUpdates(histories, updateTime)

            verifySequence {
                hakemusRepository.getOneByAlluid(42)
                hakemusRepository.save(any())
                alluStatusRepository.getReferenceById(1)
                alluStatusRepository.save(any())
            }
        }

        @Test
        fun `logs error when there are no receivers`(output: CapturedOutput) {
            every { hakemusRepository.getOneByAlluid(42) } returns
                applicationEntityWithoutCustomer()
            every { hakemusRepository.save(any()) } answers { firstArg() }
            every { alluStatusRepository.getReferenceById(1) } returns AlluStatus(1, updateTime)
            every { alluStatusRepository.save(any()) } answers { firstArg() }

            hakemusService.handleHakemusUpdates(createHistories(), updateTime)

            assertThat(output)
                .contains("No receivers found for decision ready email, not sending any.")
            verifySequence {
                hakemusRepository.getOneByAlluid(42)
                hakemusRepository.save(any())
                alluStatusRepository.getReferenceById(1)
                alluStatusRepository.save(any())
            }
        }

        @ParameterizedTest
        @EnumSource(ApplicationStatus::class, names = ["OPERATIONAL_CONDITION", "FINISHED"])
        fun `logs an error when a johtoselvityshakemus gets a supervision document`(
            status: ApplicationStatus,
            output: CapturedOutput
        ) {
            every { hakemusRepository.getOneByAlluid(alluid) } returns
                applicationEntityWithoutCustomer()
            every { hakemusRepository.save(any()) } answers { firstArg() }
            every { alluStatusRepository.getReferenceById(1) } returns AlluStatus(1, updateTime)
            every { alluStatusRepository.save(any()) } answers { firstArg() }

            hakemusService.handleHakemusUpdates(createHistories(status), updateTime)

            assertThat(output).all {
                contains("Got $status update for a cable report.")
                contains("id=$applicationId")
                contains("alluId=$alluid")
                contains("identifier=$identifier")
            }
            verifySequence {
                hakemusRepository.getOneByAlluid(42)
                hakemusRepository.save(any())
                alluStatusRepository.getReferenceById(1)
                alluStatusRepository.save(any())
            }
            verify { alluClient wasNot called }
        }

        private fun applicationEntityWithoutCustomer(id: Long = applicationId): HakemusEntity {
            val entity =
                HakemusFactory.createEntity(
                    id = id,
                    alluid = alluid,
                    applicationIdentifier = identifier,
                    userId = USERNAME,
                    hanke = HankeFactory.createMinimalEntity(id = 1, hankeTunnus = hankeTunnus),
                )
            return entity
        }

        private fun applicationEntityWithCustomer(id: Long = applicationId): HakemusEntity {
            val entity = applicationEntityWithoutCustomer(id)
            entity.yhteystiedot[ApplicationContactType.HAKIJA] =
                HakemusyhteystietoFactory.createEntity(application = entity, sahkoposti = receiver)
                    .withYhteyshenkilo(
                        permission = PermissionFactory.createEntity(userId = USERNAME))
            return entity
        }

        private fun createHistories(status: ApplicationStatus = ApplicationStatus.DECISION) =
            listOf(
                ApplicationHistoryFactory.create(
                    alluid,
                    ApplicationHistoryFactory.createEvent(
                        applicationIdentifier = identifier,
                        newStatus = status,
                    )),
            )
    }

    companion object {
        private val applicationData: JohtoselvityshakemusEntityData =
            ApplicationFactory.createCableReportApplicationData()

        @JvmStatic
        private fun invalidData(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    applicationData.copy(endTime = null),
                    "applicationData.endTime",
                ),
                Arguments.of(
                    applicationData.copy(startTime = null),
                    "applicationData.startTime",
                ),
                Arguments.of(
                    applicationData.copy(rockExcavation = null),
                    "applicationData.rockExcavation",
                ),
            )
    }
}
