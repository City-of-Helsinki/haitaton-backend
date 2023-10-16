package fi.hel.haitaton.hanke.application

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasClass
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import fi.hel.haitaton.hanke.HankeEntity
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.allu.AlluException
import fi.hel.haitaton.hanke.allu.AlluLoginException
import fi.hel.haitaton.hanke.allu.AlluStatus
import fi.hel.haitaton.hanke.allu.AlluStatusRepository
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.CableReportService
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentService
import fi.hel.haitaton.hanke.configuration.Feature
import fi.hel.haitaton.hanke.configuration.FeatureFlags
import fi.hel.haitaton.hanke.email.EmailSenderService
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.withContacts
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.withCustomer
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import fi.hel.haitaton.hanke.geometria.GeometriatDao
import fi.hel.haitaton.hanke.logging.ApplicationLoggingService
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.logging.HankeLoggingService
import fi.hel.haitaton.hanke.logging.Status
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.validation.InvalidApplicationDataException
import io.mockk.Called
import io.mockk.called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyAll
import io.mockk.verifySequence
import java.time.OffsetDateTime
import java.util.stream.Stream
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension

private const val USERNAME = "test"
private const val HANKE_TUNNUS = HankeFactory.defaultHankeTunnus

class ApplicationServiceTest {
    private val applicationRepository: ApplicationRepository = mockk()
    private val hankeRepository: HankeRepository = mockk()
    private val statusRepository: AlluStatusRepository = mockk()
    private val geometriatDao: GeometriatDao = mockk()

    private val cableReportService: CableReportService = mockk()
    private val permissionService: PermissionService = mockk()
    private val emailSenderService: EmailSenderService = mockk()
    private val attachmentService: ApplicationAttachmentService = mockk()

    private val disclosureLogService: DisclosureLogService = mockk(relaxUnitFun = true)
    private val loggingService: ApplicationLoggingService = mockk(relaxUnitFun = true)
    private val hankeKayttajaService: HankeKayttajaService = mockk(relaxUnitFun = true)
    private val hankeLoggingService: HankeLoggingService = mockk(relaxUnitFun = true)

    private val featureFlags: FeatureFlags = mockk(relaxed = true)

    private val applicationService: ApplicationService =
        ApplicationService(
            applicationRepository,
            statusRepository,
            cableReportService,
            disclosureLogService,
            loggingService,
            hankeKayttajaService,
            emailSenderService,
            attachmentService,
            geometriatDao,
            permissionService,
            hankeRepository,
            hankeLoggingService,
            featureFlags
        )

    companion object {
        private val applicationData: CableReportApplicationData =
            "/fi/hel/haitaton/hanke/application/applicationData.json".asJsonResource()

        @JvmStatic
        private fun invalidData(): Stream<Arguments> =
            Stream.of(
                Arguments.of(
                    applicationData.copy(
                        customerWithContacts =
                            applicationData.customerWithContacts.copy(
                                customer =
                                    applicationData.customerWithContacts.customer.copy(type = null)
                            )
                    ),
                    "applicationData.customerWithContacts.customer.type",
                ),
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

    @BeforeEach
    fun cleanup() {
        clearAllMocks()
    }

    @AfterEach
    fun verifyMocks() {
        checkUnnecessaryStub()
        confirmVerified(
            applicationRepository,
            statusRepository,
            cableReportService,
            disclosureLogService,
            loggingService,
            hankeKayttajaService,
            emailSenderService,
            geometriatDao,
            permissionService,
            hankeRepository,
        )
    }

    @Nested
    inner class CreateApplication {
        @Test
        fun `when valid data should create`() {
            val application = application()
            val hanke = HankeFactory.createMinimalEntity(id = 1)
            every { applicationRepository.save(any()) } answers
                {
                    firstArg<ApplicationEntity>().copy(id = 1)
                }
            every { hankeRepository.findByHankeTunnus(HANKE_TUNNUS) } returns hanke
            every { geometriatDao.validateGeometriat(any()) } returns null
            every { geometriatDao.isInsideHankeAlueet(1, any()) } returns true

            val created = applicationService.create(application, USERNAME)

            assertThat(created.id).isEqualTo(1)
            assertThat(created.alluid).isEqualTo(null)
            verifySequence {
                geometriatDao.validateGeometriat(any())
                hankeRepository.findByHankeTunnus(HANKE_TUNNUS)
                geometriatDao.isInsideHankeAlueet(1, any())
                applicationRepository.save(any())
                loggingService.logCreate(any(), USERNAME)
            }
            verifyAll {
                disclosureLogService wasNot Called
                cableReportService wasNot Called
            }
        }

        @Test
        fun `when invalid geometry should throw`() {
            val application = application()
            every { geometriatDao.validateGeometriat(any()) } returns
                GeometriatDao.InvalidDetail(
                    "Self-intersection",
                    """{"type":"Point","coordinates":[25494009.65639264,6679886.142116806]}"""
                )

            val exception =
                assertThrows<ApplicationGeometryException> {
                    applicationService.create(application, USERNAME)
                }

            assertThat(exception)
                .hasMessage(
                    """Invalid geometry received when creating a new application for user $USERNAME, reason = Self-intersection, location = {"type":"Point","coordinates":[25494009.65639264,6679886.142116806]}"""
                )
            verifySequence { geometriatDao.validateGeometriat(any()) }
        }
    }

    @Nested
    inner class UpdateApplication {
        @Test
        fun `when update Allu data should save disclosure logs`() {
            val hanke = HankeFactory.createMinimalEntity(id = 1)
            val applicationEntity = applicationEntity(alluId = 42, hanke = hanke)
            val sender = HankeKayttajaFactory.createEntity()
            every { applicationRepository.findOneById(3) } returns applicationEntity
            every { applicationRepository.save(applicationEntity) } returns applicationEntity
            justRun { cableReportService.update(42, any()) }
            justRun { cableReportService.addAttachment(42, any()) }
            every { cableReportService.getApplicationInformation(42) } returns
                AlluDataFactory.createAlluApplicationResponse()
            every { geometriatDao.validateGeometriat(any()) } returns null
            every { geometriatDao.isInsideHankeAlueet(1, any()) } returns true
            every { geometriatDao.calculateCombinedArea(any()) } returns 100f
            every { geometriatDao.calculateArea(any()) } returns 100f
            every { hankeKayttajaService.getKayttajaByUserId(1, USERNAME) } returns sender
            val updatedData =
                applicationData.copy(rockExcavation = !applicationData.rockExcavation!!)

            applicationService.updateApplicationData(3, updatedData, USERNAME)

            verifySequence {
                applicationRepository.findOneById(3)
                geometriatDao.validateGeometriat(any())
                geometriatDao.isInsideHankeAlueet(1, any())
                cableReportService.getApplicationInformation(42)
                applicationRepository.save(applicationEntity)
                geometriatDao.calculateCombinedArea(
                    listOf(applicationData.areas?.first()?.geometry!!)
                )
                geometriatDao.calculateArea(any())
                cableReportService.update(42, any())
                disclosureLogService.saveDisclosureLogsForAllu(3, updatedData, Status.SUCCESS)
                cableReportService.addAttachment(42, any())
                hankeKayttajaService.getKayttajaByUserId(1, USERNAME)
                hankeKayttajaService.saveNewTokensFromApplication(
                    applicationEntity,
                    1,
                    HANKE_TUNNUS,
                    HankeFactory.defaultNimi,
                    USERNAME,
                    sender
                )
                loggingService.logUpdate(any(), any(), USERNAME)
            }
        }

        @Test
        fun `when user management disabled should not create tokens`() {
            val hanke = HankeFactory.createMinimalEntity(generated = true)
            val applicationEntity = applicationEntity(alluId = 42, hanke = hanke)
            val dataupdate = applicationData.copy(name = "New name")
            every { applicationRepository.findOneById(3) } returns applicationEntity
            every { geometriatDao.validateGeometriat(any()) } returns null
            every { cableReportService.getApplicationInformation(42) } returns
                AlluDataFactory.createAlluApplicationResponse()
            every { applicationRepository.save(applicationEntity) } returns applicationEntity
            every { geometriatDao.calculateCombinedArea(any()) } returns 100f
            every { geometriatDao.calculateArea(any()) } returns 100f
            justRun { cableReportService.update(42, any()) }
            justRun { cableReportService.addAttachment(42, any()) }
            every { featureFlags.isDisabled(Feature.USER_MANAGEMENT) } returns true

            applicationService.updateApplicationData(
                id = 3,
                newApplicationData = dataupdate,
                userId = USERNAME
            )

            verifySequence {
                applicationRepository.findOneById(3)
                geometriatDao.validateGeometriat(any())
                cableReportService.getApplicationInformation(42)
                applicationRepository.save(any())
                geometriatDao.calculateCombinedArea(any())
                geometriatDao.calculateArea(any())
                cableReportService.update(42, any())
                disclosureLogService.saveDisclosureLogsForAllu(3, any(), any())
                cableReportService.addAttachment(42, any())
                loggingService.logUpdate(any(), any(), USERNAME)
            }
            verifyAll {
                hankeKayttajaService wasNot Called
                emailSenderService wasNot Called
            }
        }

        @Test
        fun `when invalid geometry updateApplicationData should throw`() {
            val applicationEntity =
                applicationEntity(alluId = 42, hanke = HankeFactory.createMinimalEntity())
            every { applicationRepository.findOneById(3) } returns applicationEntity
            every { geometriatDao.validateGeometriat(any()) } returns
                GeometriatDao.InvalidDetail(
                    "Self-intersection",
                    """{"type":"Point","coordinates":[25494009.65639264,6679886.142116806]}"""
                )
            val updatedData =
                applicationData.copy(rockExcavation = !applicationData.rockExcavation!!)

            val exception =
                assertThrows<ApplicationGeometryException> {
                    applicationService.updateApplicationData(3, updatedData, USERNAME)
                }

            assertThat(exception)
                .hasMessage(
                    """Invalid geometry received when updating application for user $USERNAME, id=3, alluid=42, reason = Self-intersection, location = {"type":"Point","coordinates":[25494009.65639264,6679886.142116806]}"""
                )
            verifySequence {
                applicationRepository.findOneById(3)
                geometriatDao.validateGeometriat(any())
            }
        }
    }

    @Nested
    inner class SendApplication {
        @Test
        fun `when successful should save disclosure logs`() {
            val hankeEntity = HankeFactory.createMinimalEntity(id = 1)
            val applicationEntity = applicationEntity(hanke = hankeEntity)
            val sender = HankeKayttajaFactory.createEntity()
            every { applicationRepository.findOneById(3) } returns applicationEntity
            every { applicationRepository.save(any()) } answers { firstArg() }
            every { cableReportService.create(any()) } returns 42
            justRun { cableReportService.addAttachment(42, any()) }
            every { cableReportService.getApplicationInformation(42) } returns
                AlluDataFactory.createAlluApplicationResponse()
            every { geometriatDao.calculateCombinedArea(any()) } returns 100f
            every { geometriatDao.calculateArea(any()) } returns 100f
            every { geometriatDao.isInsideHankeAlueet(1, any()) } returns true
            justRun { attachmentService.sendInitialAttachments(42, any()) }
            every { hankeKayttajaService.getKayttajaByUserId(1, USERNAME) } returns sender
            justRun { emailSenderService.sendApplicationNotificationEmail(any()) }

            applicationService.sendApplication(3, USERNAME)

            val expectedApplication = applicationData.copy(pendingOnClient = false)
            verifySequence {
                applicationRepository.findOneById(3)
                geometriatDao.isInsideHankeAlueet(1, any())
                geometriatDao.calculateCombinedArea(any())
                geometriatDao.calculateArea(any())
                cableReportService.create(any())
                disclosureLogService.saveDisclosureLogsForAllu(
                    3,
                    expectedApplication,
                    Status.SUCCESS
                )
                cableReportService.addAttachment(42, any())
                attachmentService.sendInitialAttachments(42, any())
                cableReportService.getApplicationInformation(42)
                hankeKayttajaService.getKayttajaByUserId(1, USERNAME)
                hankeKayttajaService.saveNewTokensFromApplication(
                    applicationEntity,
                    hankeEntity.id!!,
                    hankeEntity.hankeTunnus,
                    hankeEntity.nimi,
                    USERNAME,
                    sender
                )
                emailSenderService.sendApplicationNotificationEmail(any())
                emailSenderService.sendApplicationNotificationEmail(any())
                applicationRepository.save(any())
            }
        }

        @Test
        fun `when sending fails should save disclosure logs for attempt`() {
            val hankeEntity = HankeFactory.createMinimalEntity(id = 1)
            val applicationEntity = applicationEntity(hanke = hankeEntity)
            every { applicationRepository.findOneById(3) } returns applicationEntity
            every { geometriatDao.calculateCombinedArea(any()) } returns 100f
            every { geometriatDao.calculateArea(any()) } returns 100f
            every { cableReportService.create(any()) } throws AlluException(listOf())
            every { geometriatDao.isInsideHankeAlueet(1, any()) } returns true

            assertThrows<AlluException> { applicationService.sendApplication(3, USERNAME) }

            val expectedApplication = applicationData.copy(pendingOnClient = false)
            verifySequence {
                applicationRepository.findOneById(3)
                geometriatDao.isInsideHankeAlueet(1, any())
                geometriatDao.calculateCombinedArea(any())
                geometriatDao.calculateArea(any())
                cableReportService.create(any())
                disclosureLogService.saveDisclosureLogsForAllu(
                    3,
                    expectedApplication,
                    Status.FAILED,
                    ALLU_APPLICATION_ERROR_MSG
                )
            }
            verify { hankeKayttajaService wasNot Called }
        }

        @Test
        fun `when login error should not save disclosure logs`() {
            val hankeEntity = HankeFactory.createMinimalEntity()
            val applicationEntity = applicationEntity(hanke = hankeEntity)
            assertThat(applicationEntity.applicationData.areas).isNotNull().isNotEmpty()
            every { applicationRepository.findOneById(3) } returns applicationEntity
            every { geometriatDao.calculateCombinedArea(any()) } returns 500f
            every { geometriatDao.calculateArea(any()) } returns 500f
            every { geometriatDao.isInsideHankeAlueet(any(), any()) } returns true
            every { cableReportService.create(any()) } throws AlluLoginException(RuntimeException())

            assertThrows<AlluLoginException> { applicationService.sendApplication(3, USERNAME) }

            verifySequence {
                applicationRepository.findOneById(3)
                geometriatDao.isInsideHankeAlueet(any(), any())
                geometriatDao.calculateCombinedArea(any())
                geometriatDao.calculateArea(any())
                cableReportService.create(any())
            }
            verify { disclosureLogService wasNot called }
        }

        @ParameterizedTest
        @CsvSource("true,Louhitaan", "false,Ei louhita")
        fun `when sending should add rock excavation information to work description`(
            rockExcavation: Boolean,
            expectedSuffix: String
        ) {
            val hankeEntity = HankeFactory.createMinimalEntity(id = 1)
            val applicationEntity =
                applicationEntity(
                    data = applicationData.copy(rockExcavation = rockExcavation),
                    hanke = hankeEntity
                )
            val sender = HankeKayttajaFactory.createEntity()
            every { applicationRepository.findOneById(3) } returns applicationEntity
            every { applicationRepository.save(any()) } answers { firstArg() }
            every { geometriatDao.calculateCombinedArea(any()) } returns 100f
            every { geometriatDao.calculateArea(any()) } returns 100f
            every { geometriatDao.isInsideHankeAlueet(1, any()) } returns true
            every { cableReportService.create(any()) } returns 852
            justRun { cableReportService.addAttachment(852, any()) }
            every { cableReportService.getApplicationInformation(852) } returns
                AlluDataFactory.createAlluApplicationResponse(852)
            justRun { attachmentService.sendInitialAttachments(852, any()) }
            every { hankeKayttajaService.getKayttajaByUserId(1, USERNAME) } returns sender
            justRun { emailSenderService.sendApplicationNotificationEmail(any()) }

            applicationService.sendApplication(3, USERNAME)

            val expectedApplicationData =
                applicationData.copy(pendingOnClient = false, rockExcavation = rockExcavation)
            val expectedAlluData =
                expectedApplicationData
                    .toAlluData(HANKE_TUNNUS)
                    .copy(workDescription = applicationData.workDescription + "\n" + expectedSuffix)
            verifySequence {
                applicationRepository.findOneById(3)
                geometriatDao.isInsideHankeAlueet(1, any())
                geometriatDao.calculateCombinedArea(any())
                geometriatDao.calculateArea(any())
                cableReportService.create(expectedAlluData)
                disclosureLogService.saveDisclosureLogsForAllu(
                    3,
                    expectedApplicationData,
                    Status.SUCCESS
                )
                cableReportService.addAttachment(852, any())
                cableReportService.getApplicationInformation(852)
                hankeKayttajaService.getKayttajaByUserId(1, USERNAME)
                hankeKayttajaService.saveNewTokensFromApplication(
                    any(),
                    hankeEntity.id!!,
                    hankeEntity.hankeTunnus,
                    hankeEntity.nimi,
                    USERNAME,
                    sender
                )
                emailSenderService.sendApplicationNotificationEmail(any())
                emailSenderService.sendApplicationNotificationEmail(any())
                applicationRepository.save(any())
            }
        }

        @ParameterizedTest(name = "{1} {2}")
        @MethodSource("fi.hel.haitaton.hanke.application.ApplicationServiceTest#invalidData")
        fun `when invalid data should not send application`(
            applicationData: ApplicationData,
            path: String,
        ) {
            val hankeEntity = HankeFactory.createMinimalEntity(id = 1)
            val applicationEntity = applicationEntity(data = applicationData, hanke = hankeEntity)
            every { applicationRepository.findOneById(3) } returns applicationEntity
            every { geometriatDao.isInsideHankeAlueet(1, any()) } returns true

            assertFailure { applicationService.sendApplication(3, USERNAME) }
                .all {
                    hasClass(InvalidApplicationDataException::class)
                    hasMessage("Application contains invalid data. Errors at paths: $path")
                }

            verifySequence {
                applicationRepository.findOneById(3)
                geometriatDao.isInsideHankeAlueet(1, any())
            }
            verifyAll {
                cableReportService wasNot Called
                disclosureLogService wasNot Called
                hankeKayttajaService wasNot Called
                loggingService wasNot Called
            }
        }

        @Test
        fun `when user management disabled should not create tokens`() {
            val hankeEntity = HankeFactory.createMinimalEntity(generated = true)
            val applicationEntity = applicationEntity(hanke = hankeEntity)
            every { applicationRepository.findOneById(3) } returns applicationEntity
            every { applicationRepository.save(applicationEntity) } returns applicationEntity
            every { geometriatDao.calculateCombinedArea(any()) } returns 100f
            every { geometriatDao.calculateArea(any()) } returns 100f
            every { cableReportService.getApplicationInformation(42) } returns
                AlluDataFactory.createAlluApplicationResponse()
            every { cableReportService.create(any()) } returns 42
            justRun { cableReportService.addAttachment(42, any()) }
            every { featureFlags.isDisabled(Feature.USER_MANAGEMENT) } returns true
            justRun { attachmentService.sendInitialAttachments(42, any()) }

            applicationService.sendApplication(id = 3, userId = USERNAME)

            verifySequence {
                applicationRepository.findOneById(3)
                geometriatDao.calculateCombinedArea(any())
                geometriatDao.calculateArea(any())
                cableReportService.create(any())
                disclosureLogService.saveDisclosureLogsForAllu(3, any(), any())
                cableReportService.addAttachment(42, any())
                cableReportService.getApplicationInformation(42)
                applicationRepository.save(any())
            }
            verifyAll {
                hankeKayttajaService wasNot Called
                emailSenderService wasNot Called
            }
        }
    }

    @Nested
    @ExtendWith(OutputCaptureExtension::class)
    inner class HandleApplicationUpdates {
        private val alluid = 42
        private val applicationId = 13L
        private val hankeTunnus = "HAI23-1"
        private val receiver = AlluDataFactory.teppoEmail
        private val updateTime = OffsetDateTime.parse("2022-10-09T06:36:51Z")
        private val identifier = ApplicationHistoryFactory.defaultApplicationIdentifier

        @Test
        fun `sends email to the orderer when application gets a decision`() {
            every { applicationRepository.getOneByAlluid(42) } returns
                applicationEntityWithCustomer()
            justRun {
                emailSenderService.sendJohtoselvitysCompleteEmail(
                    receiver,
                    applicationId,
                    identifier
                )
            }
            every { applicationRepository.save(any()) } answers { firstArg() }
            every { statusRepository.getReferenceById(1) } returns AlluStatus(1, updateTime)
            every { statusRepository.save(any()) } answers { firstArg() }

            applicationService.handleApplicationUpdates(historiesWithDecision(), updateTime)

            verifySequence {
                applicationRepository.getOneByAlluid(42)
                emailSenderService.sendJohtoselvitysCompleteEmail(
                    receiver,
                    applicationId,
                    identifier
                )
                applicationRepository.save(any())
                statusRepository.getReferenceById(1)
                statusRepository.save(any())
            }
        }

        @Test
        fun `doesn't send email when status is not decision`() {
            every { applicationRepository.getOneByAlluid(42) } returns
                applicationEntityWithCustomer()
            every { applicationRepository.save(any()) } answers { firstArg() }
            every { statusRepository.getReferenceById(1) } returns AlluStatus(1, updateTime)
            every { statusRepository.save(any()) } answers { firstArg() }
            val histories =
                listOf(
                    ApplicationHistoryFactory.create(
                        alluid,
                        ApplicationHistoryFactory.createEvent(
                            applicationIdentifier = identifier,
                            newStatus = ApplicationStatus.HANDLING
                        )
                    ),
                )

            applicationService.handleApplicationUpdates(histories, updateTime)

            verifySequence {
                applicationRepository.getOneByAlluid(42)
                applicationRepository.save(any())
                statusRepository.getReferenceById(1)
                statusRepository.save(any())
            }
            verify { emailSenderService wasNot Called }
        }

        @Test
        fun `logs error when there are no receivers`(output: CapturedOutput) {
            every { applicationRepository.getOneByAlluid(42) } returns
                applicationEntityWithCustomer()
                    .withCustomer(
                        AlluDataFactory.createCompanyCustomer()
                            .withContacts(AlluDataFactory.createContact(orderer = false))
                    )
            every { applicationRepository.save(any()) } answers { firstArg() }
            every { statusRepository.getReferenceById(1) } returns AlluStatus(1, updateTime)
            every { statusRepository.save(any()) } answers { firstArg() }

            applicationService.handleApplicationUpdates(historiesWithDecision(), updateTime)

            assertThat(output)
                .contains("No receivers found for decision ready email, not sending any.")
            verifySequence {
                applicationRepository.getOneByAlluid(42)
                applicationRepository.save(any())
                statusRepository.getReferenceById(1)
                statusRepository.save(any())
            }
            verify { emailSenderService wasNot Called }
        }

        @Test
        fun `logs error if receiver email is null`(output: CapturedOutput) {
            every { applicationRepository.getOneByAlluid(42) } returns
                applicationEntityWithCustomer()
                    .withCustomer(
                        AlluDataFactory.createCompanyCustomer()
                            .withContacts(
                                AlluDataFactory.createContact(orderer = true, email = null)
                            )
                    )
            every { applicationRepository.save(any()) } answers { firstArg() }
            every { statusRepository.getReferenceById(1) } returns AlluStatus(1, updateTime)
            every { statusRepository.save(any()) } answers { firstArg() }

            applicationService.handleApplicationUpdates(historiesWithDecision(), updateTime)

            assertThat(output)
                .contains("Can't send decision ready email, because contact email is null.")
            verifySequence {
                applicationRepository.getOneByAlluid(42)
                applicationRepository.save(any())
                statusRepository.getReferenceById(1)
                statusRepository.save(any())
            }
            verify { emailSenderService wasNot Called }
        }

        private fun applicationEntityWithCustomer() =
            AlluDataFactory.createApplicationEntity(
                    id = applicationId,
                    alluid = alluid,
                    applicationIdentifier = identifier,
                    userId = "user",
                    hanke = HankeFactory.createMinimalEntity(id = 1, hankeTunnus = hankeTunnus),
                )
                .withCustomer(AlluDataFactory.createCompanyCustomerWithOrderer())

        private fun historiesWithDecision() =
            listOf(
                ApplicationHistoryFactory.create(
                    alluid,
                    ApplicationHistoryFactory.createEvent(
                        applicationIdentifier = identifier,
                        newStatus = ApplicationStatus.DECISION
                    )
                ),
            )
    }

    private fun application(id: Long? = null) =
        AlluDataFactory.createApplication(
            id = id,
            applicationData = applicationData,
            hankeTunnus = HANKE_TUNNUS,
        )

    private fun applicationEntity(
        id: Long? = 3,
        alluId: Int? = null,
        data: ApplicationData = applicationData,
        hanke: HankeEntity
    ) =
        AlluDataFactory.createApplicationEntity(
            id = id,
            alluid = alluId,
            userId = USERNAME,
            applicationData = data,
            hanke = hanke,
        )
}
