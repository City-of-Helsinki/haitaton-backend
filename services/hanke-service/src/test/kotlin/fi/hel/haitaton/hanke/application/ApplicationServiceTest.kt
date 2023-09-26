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
import fi.hel.haitaton.hanke.email.EmailSenderService
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.withContacts
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.withCustomer
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.withHanke
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
import io.mockk.verifySequence
import java.time.OffsetDateTime
import java.util.stream.Stream
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.test.context.junit.jupiter.SpringExtension

private const val USERNAME = "test"
private const val HANKE_TUNNUS = "HAI-1234"

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(SpringExtension::class)
class ApplicationServiceTest {
    private val applicationRepo: ApplicationRepository = mockk()
    private val statusRepo: AlluStatusRepository = mockk()
    private val cableReportService: CableReportService = mockk()
    private val geometriatDao: GeometriatDao = mockk()
    private val disclosureLogService: DisclosureLogService = mockk(relaxUnitFun = true)
    private val applicationLoggingService: ApplicationLoggingService = mockk(relaxUnitFun = true)
    private val hankeRepository: HankeRepository = mockk()
    private val permissionService: PermissionService = mockk()
    private val emailSenderService: EmailSenderService = mockk()
    private val hankeKayttajaService: HankeKayttajaService = mockk(relaxUnitFun = true)
    private val attachmentService: ApplicationAttachmentService = mockk()
    private val hankeLoggingService: HankeLoggingService = mockk(relaxUnitFun = true)

    private val applicationService: ApplicationService =
        ApplicationService(
            applicationRepo,
            statusRepo,
            cableReportService,
            disclosureLogService,
            applicationLoggingService,
            hankeKayttajaService,
            emailSenderService,
            attachmentService,
            geometriatDao,
            permissionService,
            hankeRepository,
            hankeLoggingService,
        )

    @BeforeEach
    fun cleanup() {
        clearAllMocks()
    }

    @AfterEach
    fun verifyMocks() {
        checkUnnecessaryStub()
        confirmVerified(
            applicationRepo,
            statusRepo,
            cableReportService,
            disclosureLogService,
            applicationLoggingService,
            hankeKayttajaService,
            emailSenderService,
            geometriatDao,
            permissionService,
            hankeRepository,
        )
    }

    private val applicationData: CableReportApplicationData =
        "/fi/hel/haitaton/hanke/application/applicationData.json".asJsonResource()

    @Test
    fun create() {
        val dto =
            AlluDataFactory.createApplication(
                id = null,
                applicationData = applicationData,
                hankeTunnus = HANKE_TUNNUS,
            )
        every { applicationRepo.save(any()) } answers
            {
                val application: ApplicationEntity = firstArg()
                application.copy(id = 1)
            }
        val hanke = HankeEntity(id = 1, hankeTunnus = HANKE_TUNNUS)
        every { hankeRepository.findByHankeTunnus(HANKE_TUNNUS) } returns hanke
        every { geometriatDao.validateGeometriat(any()) } returns null
        every { geometriatDao.isInsideHankeAlueet(1, any()) } returns true

        val created = applicationService.create(dto, USERNAME)

        assertThat(created.id).isEqualTo(1)
        assertThat(created.alluid).isEqualTo(null)
        verifySequence {
            geometriatDao.validateGeometriat(any())
            hankeRepository.findByHankeTunnus(HANKE_TUNNUS)
            geometriatDao.isInsideHankeAlueet(1, any())
            applicationRepo.save(any())
            applicationLoggingService.logCreate(any(), USERNAME)
            disclosureLogService wasNot Called
            cableReportService wasNot Called
        }
    }

    @Test
    fun `create throws exception with invalid geometry`() {
        val dto =
            AlluDataFactory.createApplication(
                id = null,
                applicationData = applicationData,
                hankeTunnus = HANKE_TUNNUS
            )
        every { geometriatDao.validateGeometriat(any()) } returns
            GeometriatDao.InvalidDetail(
                "Self-intersection",
                """{"type":"Point","coordinates":[25494009.65639264,6679886.142116806]}"""
            )

        val exception =
            assertThrows<ApplicationGeometryException> { applicationService.create(dto, USERNAME) }

        assertThat(exception)
            .hasMessage(
                """Invalid geometry received when creating a new application for user $USERNAME, reason = Self-intersection, location = {"type":"Point","coordinates":[25494009.65639264,6679886.142116806]}"""
            )
        verifySequence { geometriatDao.validateGeometriat(any()) }
    }

    @Test
    fun `updateApplicationData saves disclosure logs when updating Allu data`() {
        val hanke = HankeEntity(id = 1, hankeTunnus = HANKE_TUNNUS)
        val applicationEntity =
            AlluDataFactory.createApplicationEntity(
                id = 3,
                alluid = 42,
                userId = USERNAME,
                applicationData = applicationData,
                hanke = hanke,
            )
        every { applicationRepo.findOneById(3) } returns applicationEntity
        every { applicationRepo.save(applicationEntity) } returns applicationEntity
        justRun { cableReportService.update(42, any()) }
        justRun { cableReportService.addAttachment(42, any()) }
        every { cableReportService.getApplicationInformation(42) } returns
            AlluDataFactory.createAlluApplicationResponse(42)
        every { geometriatDao.validateGeometriat(any()) } returns null
        every { geometriatDao.isInsideHankeAlueet(1, any()) } returns true
        every { geometriatDao.calculateCombinedArea(any()) } returns 100f
        every { geometriatDao.calculateArea(any()) } returns 100f
        val updatedData = applicationData.copy(rockExcavation = !applicationData.rockExcavation!!)

        applicationService.updateApplicationData(3, updatedData, USERNAME)

        verifySequence {
            applicationRepo.findOneById(3)
            geometriatDao.validateGeometriat(any())
            geometriatDao.isInsideHankeAlueet(1, any())
            cableReportService.getApplicationInformation(42)
            // any() here tries to match eq([]) for some reason
            geometriatDao.calculateCombinedArea(listOf(applicationData.areas?.first()?.geometry!!))
            geometriatDao.calculateArea(any())
            cableReportService.update(42, any())
            disclosureLogService.saveDisclosureLogsForAllu(updatedData, Status.SUCCESS)
            cableReportService.addAttachment(42, any())
            applicationRepo.save(applicationEntity)
            applicationLoggingService.logUpdate(any(), any(), USERNAME)
        }
    }

    @Test
    fun `updateApplicationData throws exception with invalid geometry`() {
        val applicationEntity =
            AlluDataFactory.createApplicationEntity(
                id = 3,
                alluid = 42,
                userId = USERNAME,
                applicationData = applicationData,
                hanke = HankeEntity(hankeTunnus = HANKE_TUNNUS),
            )
        every { applicationRepo.findOneById(3) } returns applicationEntity
        every { geometriatDao.validateGeometriat(any()) } returns
            GeometriatDao.InvalidDetail(
                "Self-intersection",
                """{"type":"Point","coordinates":[25494009.65639264,6679886.142116806]}"""
            )
        val updatedData = applicationData.copy(rockExcavation = !applicationData.rockExcavation!!)

        val exception =
            assertThrows<ApplicationGeometryException> {
                applicationService.updateApplicationData(3, updatedData, USERNAME)
            }

        assertThat(exception)
            .hasMessage(
                """Invalid geometry received when updating application for user $USERNAME, id=3, alluid=42, reason = Self-intersection, location = {"type":"Point","coordinates":[25494009.65639264,6679886.142116806]}"""
            )
        verifySequence {
            applicationRepo.findOneById(3)
            geometriatDao.validateGeometriat(any())
        }
    }

    @Test
    fun `sendApplication saves disclosure logs for successful attempts`() {
        val hankeEntity =
            HankeEntity(id = 1, nimi = HankeFactory.defaultNimi, hankeTunnus = HANKE_TUNNUS)
        val applicationEntity =
            AlluDataFactory.createApplicationEntity(
                id = 3,
                alluid = null,
                userId = USERNAME,
                applicationData = applicationData,
                hanke = hankeEntity,
            )
        val sender = HankeKayttajaFactory.createEntity()
        every { applicationRepo.findOneById(3) } returns applicationEntity
        every { applicationRepo.save(any()) } answers { firstArg() }
        every { cableReportService.create(any()) } returns 42
        justRun { cableReportService.addAttachment(42, any()) }
        every { cableReportService.getApplicationInformation(42) } returns
            AlluDataFactory.createAlluApplicationResponse(42)
        every { geometriatDao.calculateCombinedArea(any()) } returns 100f
        every { geometriatDao.calculateArea(any()) } returns 100f
        every { geometriatDao.isInsideHankeAlueet(1, any()) } returns true
        justRun { attachmentService.sendInitialAttachments(42, any()) }
        every { hankeKayttajaService.getKayttajaByUserId(1, USERNAME) } returns sender
        justRun { emailSenderService.sendApplicationNotificationEmail(any()) }

        applicationService.sendApplication(3, USERNAME)

        val expectedApplication = applicationData.copy(pendingOnClient = false)
        verifySequence {
            applicationRepo.findOneById(3)
            geometriatDao.isInsideHankeAlueet(1, any())
            geometriatDao.calculateCombinedArea(any())
            geometriatDao.calculateArea(any())
            cableReportService.create(any())
            disclosureLogService.saveDisclosureLogsForAllu(expectedApplication, Status.SUCCESS)
            cableReportService.addAttachment(42, any())
            attachmentService.sendInitialAttachments(42, any())
            cableReportService.getApplicationInformation(42)
            hankeKayttajaService.getKayttajaByUserId(1, USERNAME)
            hankeKayttajaService.saveNewTokensFromApplication(
                applicationEntity,
                hankeEntity.id!!,
                hankeEntity.hankeTunnus!!,
                hankeEntity.nimi!!,
                USERNAME,
                sender
            )
            emailSenderService.sendApplicationNotificationEmail(any())
            emailSenderService.sendApplicationNotificationEmail(any())
            applicationRepo.save(any())
        }
    }

    @Test
    fun `sendApplication saves disclosure logs for failed attempts`() {
        val hankeEntity =
            HankeEntity(hankeTunnus = HANKE_TUNNUS, id = 1, nimi = HankeFactory.defaultNimi)
        val applicationEntity =
            AlluDataFactory.createApplicationEntity(
                id = 3,
                alluid = null,
                userId = USERNAME,
                applicationData = applicationData,
                hanke = hankeEntity,
            )
        every { applicationRepo.findOneById(3) } returns applicationEntity
        every { geometriatDao.calculateCombinedArea(any()) } returns 100f
        every { geometriatDao.calculateArea(any()) } returns 100f
        every { cableReportService.create(any()) } throws AlluException(listOf())
        every { geometriatDao.isInsideHankeAlueet(1, any()) } returns true

        assertThrows<AlluException> { applicationService.sendApplication(3, USERNAME) }

        val expectedApplication = applicationData.copy(pendingOnClient = false)
        verifySequence {
            hankeKayttajaService wasNot Called
            applicationRepo.findOneById(3)
            geometriatDao.isInsideHankeAlueet(1, any())
            geometriatDao.calculateCombinedArea(any())
            geometriatDao.calculateArea(any())
            cableReportService.create(any())
            disclosureLogService.saveDisclosureLogsForAllu(
                expectedApplication,
                Status.FAILED,
                ALLU_APPLICATION_ERROR_MSG
            )
        }
    }

    @Test
    fun `sendApplication doesn't save disclosure logs for login errors`() {
        val hankeEntity =
            HankeEntity(hankeTunnus = HANKE_TUNNUS, id = 1, nimi = HankeFactory.defaultNimi)
        val applicationEntity =
            AlluDataFactory.createApplicationEntity(
                id = 3,
                alluid = null,
                userId = USERNAME,
                applicationData = applicationData,
                hanke = hankeEntity,
            )
        assertThat(applicationEntity.applicationData.areas).isNotNull().isNotEmpty()
        every { applicationRepo.findOneById(3) } returns applicationEntity
        every { geometriatDao.calculateCombinedArea(any()) } returns 500f
        every { geometriatDao.calculateArea(any()) } returns 500f
        every { geometriatDao.isInsideHankeAlueet(any(), any()) } returns true
        every { cableReportService.create(any()) } throws AlluLoginException(RuntimeException())

        assertThrows<AlluLoginException> { applicationService.sendApplication(3, USERNAME) }

        verifySequence {
            disclosureLogService wasNot called
            applicationRepo.findOneById(3)
            geometriatDao.isInsideHankeAlueet(any(), any())
            geometriatDao.calculateCombinedArea(any())
            geometriatDao.calculateArea(any())
            cableReportService.create(any())
        }
    }

    @ParameterizedTest
    @CsvSource("true,Louhitaan", "false,Ei louhita")
    fun `sendApplication adds rock excavation information to work description`(
        rockExcavation: Boolean,
        expectedSuffix: String
    ) {
        val hankeEntity =
            HankeEntity(hankeTunnus = HANKE_TUNNUS, id = 1, nimi = HankeFactory.defaultNimi)
        val applicationEntity =
            AlluDataFactory.createApplicationEntity(
                id = 3,
                alluid = null,
                userId = USERNAME,
                applicationData = applicationData.copy(rockExcavation = rockExcavation),
                hanke = hankeEntity,
            )
        val sender = HankeKayttajaFactory.createEntity()
        every { applicationRepo.findOneById(3) } returns applicationEntity
        every { applicationRepo.save(any()) } answers { firstArg() }
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
            applicationRepo.findOneById(3)
            geometriatDao.isInsideHankeAlueet(1, any())
            geometriatDao.calculateCombinedArea(any())
            geometriatDao.calculateArea(any())
            cableReportService.create(expectedAlluData)
            disclosureLogService.saveDisclosureLogsForAllu(expectedApplicationData, Status.SUCCESS)
            cableReportService.addAttachment(852, any())
            cableReportService.getApplicationInformation(852)
            hankeKayttajaService.getKayttajaByUserId(1, USERNAME)
            hankeKayttajaService.saveNewTokensFromApplication(
                any(),
                hankeEntity.id!!,
                hankeEntity.hankeTunnus!!,
                hankeEntity.nimi!!,
                USERNAME,
                sender
            )
            emailSenderService.sendApplicationNotificationEmail(any())
            emailSenderService.sendApplicationNotificationEmail(any())
            applicationRepo.save(any())
        }
    }

    @ParameterizedTest(name = "{1} {2}")
    @MethodSource("invalidApplicationData")
    fun `sendApplication with invalid data doesn't send application to Allu`(
        applicationData: ApplicationData,
        path: String,
    ) {
        val hankeEntity =
            HankeEntity(hankeTunnus = HANKE_TUNNUS, id = 1, nimi = HankeFactory.defaultNimi)
        val applicationEntity =
            AlluDataFactory.createApplicationEntity(
                id = 3,
                alluid = null,
                userId = USERNAME,
                applicationData = applicationData,
                hanke = hankeEntity,
            )
        every { applicationRepo.findOneById(3) } returns applicationEntity
        every { geometriatDao.isInsideHankeAlueet(1, any()) } returns true

        assertFailure { applicationService.sendApplication(3, USERNAME) }
            .all {
                hasClass(InvalidApplicationDataException::class)
                hasMessage("Application contains invalid data. Errors at paths: $path")
            }

        verifySequence {
            applicationRepo.findOneById(3)
            geometriatDao.isInsideHankeAlueet(1, any())
            cableReportService wasNot Called
            disclosureLogService wasNot Called
            hankeKayttajaService wasNot Called
            applicationLoggingService wasNot Called
        }
    }

    private fun invalidApplicationData(): Stream<Arguments> {
        return Stream.of(
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
            every { applicationRepo.getOneByAlluid(42) } returns applicationEntity()
            justRun {
                emailSenderService.sendJohtoselvitysCompleteEmail(
                    receiver,
                    applicationId,
                    identifier
                )
            }
            every { applicationRepo.save(any()) } answers { firstArg() }
            every { statusRepo.getReferenceById(1) } returns AlluStatus(1, updateTime)
            every { statusRepo.save(any()) } answers { firstArg() }

            applicationService.handleApplicationUpdates(historiesWithDecision(), updateTime)

            verifySequence {
                applicationRepo.getOneByAlluid(42)
                emailSenderService.sendJohtoselvitysCompleteEmail(
                    receiver,
                    applicationId,
                    identifier
                )
                applicationRepo.save(any())
                statusRepo.getReferenceById(1)
                statusRepo.save(any())
            }
        }

        @Test
        fun `doesn't send email when status is not decision`() {
            every { applicationRepo.getOneByAlluid(42) } returns applicationEntity()
            every { applicationRepo.save(any()) } answers { firstArg() }
            every { statusRepo.getReferenceById(1) } returns AlluStatus(1, updateTime)
            every { statusRepo.save(any()) } answers { firstArg() }
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
                applicationRepo.getOneByAlluid(42)
                applicationRepo.save(any())
                statusRepo.getReferenceById(1)
                statusRepo.save(any())
            }
            verify { emailSenderService wasNot Called }
        }

        @Test
        fun `logs error when there are no receivers`(output: CapturedOutput) {
            every { applicationRepo.getOneByAlluid(42) } returns
                applicationEntity()
                    .withCustomer(
                        AlluDataFactory.createCompanyCustomer()
                            .withContacts(AlluDataFactory.createContact(orderer = false))
                    )
            every { applicationRepo.save(any()) } answers { firstArg() }
            every { statusRepo.getReferenceById(1) } returns AlluStatus(1, updateTime)
            every { statusRepo.save(any()) } answers { firstArg() }

            applicationService.handleApplicationUpdates(historiesWithDecision(), updateTime)

            assertThat(output)
                .contains("No receivers found for decision ready email, not sending any.")
            verifySequence {
                applicationRepo.getOneByAlluid(42)
                applicationRepo.save(any())
                statusRepo.getReferenceById(1)
                statusRepo.save(any())
            }
            verify { emailSenderService wasNot Called }
        }

        @Test
        fun `logs error if hanketunnus is null`(output: CapturedOutput) {
            every { applicationRepo.getOneByAlluid(42) } returns
                applicationEntity().withHanke(HankeEntity(id = 1, hankeTunnus = null))
            every { applicationRepo.save(any()) } answers { firstArg() }
            every { statusRepo.getReferenceById(1) } returns AlluStatus(1, updateTime)
            every { statusRepo.save(any()) } answers { firstArg() }

            applicationService.handleApplicationUpdates(historiesWithDecision(), updateTime)

            assertThat(output)
                .contains("Can't send decision ready emails, because hankeTunnus is null.")
            verifySequence {
                applicationRepo.getOneByAlluid(42)
                applicationRepo.save(any())
                statusRepo.getReferenceById(1)
                statusRepo.save(any())
            }
            verify { emailSenderService wasNot Called }
        }

        @Test
        fun `logs error if receiver email is null`(output: CapturedOutput) {
            every { applicationRepo.getOneByAlluid(42) } returns
                applicationEntity()
                    .withCustomer(
                        AlluDataFactory.createCompanyCustomer()
                            .withContacts(
                                AlluDataFactory.createContact(orderer = true, email = null)
                            )
                    )
            every { applicationRepo.save(any()) } answers { firstArg() }
            every { statusRepo.getReferenceById(1) } returns AlluStatus(1, updateTime)
            every { statusRepo.save(any()) } answers { firstArg() }

            applicationService.handleApplicationUpdates(historiesWithDecision(), updateTime)

            assertThat(output)
                .contains("Can't send decision ready email, because contact email is null.")
            verifySequence {
                applicationRepo.getOneByAlluid(42)
                applicationRepo.save(any())
                statusRepo.getReferenceById(1)
                statusRepo.save(any())
            }
            verify { emailSenderService wasNot Called }
        }

        private fun applicationEntity() =
            AlluDataFactory.createApplicationEntity(
                    id = applicationId,
                    alluid = alluid,
                    applicationIdentifier = identifier,
                    userId = "user",
                    hanke = HankeEntity(id = 1, hankeTunnus = hankeTunnus),
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
}
