package fi.hel.haitaton.hanke.application

import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.junit.jupiter.SpringExtension

private const val username = "testUser"

@ExtendWith(SpringExtension::class)
@WithMockUser(username)
class ApplicationControllerTest {

    private val applicationService: ApplicationService = mockk()
    private val disclosureLogService: DisclosureLogService = mockk(relaxUnitFun = true)

    private val applicationController =
        ApplicationController(applicationService, disclosureLogService)

    @AfterEach
    fun cleanUp() {
        // TODO: Needs newer MockK, which needs newer Spring test dependencies
        // checkUnnecessaryStub()
        confirmVerified(applicationService, disclosureLogService)
        clearAllMocks()
    }

    @Test
    fun `getAll saves disclosure logs`() {
        val applications =
            listOf(
                AlluDataFactory.createApplication(),
                AlluDataFactory.createApplication(
                    id = 2,
                    applicationData =
                        AlluDataFactory.createCableReportApplicationData(name = "Toinen selvitys")
                ),
            )
        every { applicationService.getAllApplicationsForUser(username) } returns applications

        applicationController.getAll()

        verify {
            disclosureLogService.saveDisclosureLogsForApplications(applications, username)
            applicationService.getAllApplicationsForUser(username)
        }
    }

    @Test
    fun `getById saves disclosure logs`() {
        val application = AlluDataFactory.createApplication()
        every { applicationService.getApplicationById(1, username) } returns application

        applicationController.getById(1)

        verify {
            disclosureLogService.saveDisclosureLogsForApplication(application, username)
            applicationService.getApplicationById(1, username)
        }
    }

    @Test
    fun `create saves disclosure logs`() {
        val requestApplication = AlluDataFactory.createApplication(id = null)
        val createdApplication = requestApplication.copy(id = 1)
        every { applicationService.create(requestApplication, username) } returns createdApplication

        applicationController.create(requestApplication)

        verify {
            disclosureLogService.saveDisclosureLogsForApplication(createdApplication, username)
            applicationService.create(requestApplication, username)
        }
    }

    @Test
    fun `update saves disclosure logs`() {
        val application = AlluDataFactory.createApplication(id = 1)
        every {
            applicationService.updateApplicationData(1, application.applicationData, username)
        } returns application

        applicationController.update(1, application)

        verify {
            disclosureLogService.saveDisclosureLogsForApplication(application, username)
            applicationService.updateApplicationData(1, application.applicationData, username)
        }
    }
}
