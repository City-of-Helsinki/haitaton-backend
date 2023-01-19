package fi.hel.haitaton.hanke.allu

import assertk.assertThat
import assertk.assertions.isEqualTo
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.logging.ApplicationLoggingService
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.logging.Status
import io.mockk.called
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension

private const val username = "test"

@ExtendWith(SpringExtension::class)
class ApplicationServiceTest {
    private val applicationRepo: ApplicationRepository = mockk()
    private val statusRepo: AlluStatusRepository = mockk()
    private val cableReportService: CableReportService = mockk()
    private val disclosureLogService: DisclosureLogService = mockk(relaxUnitFun = true)
    private val applicationLoggingService: ApplicationLoggingService = mockk(relaxUnitFun = true)

    private val service: ApplicationService =
        ApplicationService(
            applicationRepo,
            statusRepo,
            cableReportService,
            disclosureLogService,
            applicationLoggingService,
        )

    @BeforeEach
    fun cleanup() {
        // TODO: Needs newer MockK, which needs newer Spring test dependencies
        // checkUnnecessaryStub()
        clearAllMocks()
    }

    @AfterEach
    fun verifyMocks() {
        confirmVerified(
            applicationRepo,
            statusRepo,
            cableReportService,
            disclosureLogService,
            applicationLoggingService
        )
    }

    private val applicationData: CableReportApplicationData =
        "/fi/hel/haitaton/hanke/application/applicationData.json".asJsonResource()

    @Test
    fun create() {
        val dto = AlluDataFactory.createApplication(id = null, applicationData = applicationData)
        every { cableReportService.create(any()) } returns 42
        every { applicationRepo.save(any()) } answers
            {
                val application: ApplicationEntity = firstArg()
                application.copy(id = 1)
            }

        val created = service.create(dto, username)

        assertThat(created.id).isEqualTo(1)
        assertThat(created.alluid).isEqualTo(42)
        verify {
            disclosureLogService.saveDisclosureLogsForAllu(applicationData, Status.SUCCESS)
            cableReportService.create(any())
            applicationRepo.save(any())
            applicationLoggingService.logCreate(any(), username)
        }
    }

    @Test
    fun `updateApplicationData saves disclosure logs when updating Allu data`() {
        val applicationEntity =
            AlluDataFactory.createApplicationEntity(
                id = 3,
                alluid = 42,
                userId = username,
                applicationData = applicationData,
            )
        every { applicationRepo.findOneByIdAndUserId(3, username) } returns applicationEntity
        every { applicationRepo.save(applicationEntity) } returns applicationEntity
        justRun { cableReportService.update(42, any()) }
        every { cableReportService.getCurrentStatus(42) } returns null

        service.updateApplicationData(3, applicationData, username)

        verify {
            disclosureLogService.saveDisclosureLogsForAllu(applicationData, Status.SUCCESS)
            applicationRepo.findOneByIdAndUserId(3, username)
            applicationRepo.save(applicationEntity)
            cableReportService.update(42, any())
            cableReportService.getCurrentStatus(42)
            applicationLoggingService.logUpdate(any(), any(), username)
        }
    }

    @Test
    fun `sendApplication saves disclosure logs for successful attempts`() {
        val applicationEntity =
            AlluDataFactory.createApplicationEntity(
                id = 3,
                alluid = null,
                userId = username,
                applicationData = applicationData
            )
        every { applicationRepo.findOneByIdAndUserId(3, username) } returns applicationEntity
        every { applicationRepo.save(any()) } answers { firstArg() }
        every { cableReportService.create(any()) } returns 42

        service.sendApplication(3, username)

        val expectedApplication = applicationData.copy(pendingOnClient = false)
        verify {
            disclosureLogService.saveDisclosureLogsForAllu(expectedApplication, Status.SUCCESS)
            applicationRepo.findOneByIdAndUserId(3, username)
            applicationRepo.save(any())
            cableReportService.create(any())
        }
    }

    @Test
    fun `sendApplication saves disclosure logs for failed attempts`() {
        val applicationEntity =
            AlluDataFactory.createApplicationEntity(
                id = 3,
                alluid = null,
                userId = username,
                applicationData = applicationData
            )
        every { applicationRepo.findOneByIdAndUserId(3, username) } returns applicationEntity
        every { cableReportService.create(any()) } throws RuntimeException()

        assertThrows<RuntimeException> { service.sendApplication(3, username) }

        val expectedApplication = applicationData.copy(pendingOnClient = false)
        verify {
            disclosureLogService.saveDisclosureLogsForAllu(
                expectedApplication,
                Status.FAILED,
                ALLU_APPLICATION_ERROR_MSG
            )
            applicationRepo.findOneByIdAndUserId(3, username)
            cableReportService.create(any())
        }
    }

    @Test
    fun `sendApplication doesn't save disclosure logs for login errors`() {
        val applicationEntity =
            AlluDataFactory.createApplicationEntity(
                id = 3,
                alluid = null,
                userId = username,
                applicationData = applicationData
            )
        every { applicationRepo.findOneByIdAndUserId(3, username) } returns applicationEntity
        every { cableReportService.create(any()) } throws AlluLoginException(RuntimeException())

        assertThrows<AlluLoginException> { service.sendApplication(3, username) }

        verify {
            disclosureLogService wasNot called
            applicationRepo.findOneByIdAndUserId(3, username)
            cableReportService.create(any())
        }
    }
}
