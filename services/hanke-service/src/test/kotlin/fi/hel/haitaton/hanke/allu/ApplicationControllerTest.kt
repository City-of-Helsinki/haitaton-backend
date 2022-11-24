package fi.hel.haitaton.hanke.allu

import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
@WithMockUser("testUser")
class ApplicationControllerTest {
    private val username = "testUser"

    private val applicationService: ApplicationService = mockk()
    private val disclosureLogService: DisclosureLogService = mockk(relaxUnitFun = true)

    private val applicationController =
        ApplicationController(applicationService, disclosureLogService)

    @AfterEach
    fun cleanUp() {
        // TODO: Needs newer MockK, which needs newer Spring test dependencies
        // checkUnnecessaryStub()
        clearAllMocks()
    }

    @Test
    fun `getAll saves disclosure logs`() {
        val applications =
            listOf(
                AlluDataFactory.createApplicationDto(),
                AlluDataFactory.createApplicationDto(
                    id = 2,
                    applicationData =
                        AlluDataFactory.createCableReportApplication(name = "Toinen selvitys")
                ),
            )
        every { applicationService.getAllApplicationsForCurrentUser() } returns applications

        applicationController.getAll()

        verify { disclosureLogService.saveDisclosureLogsForApplications(applications, username) }
    }

    @Test
    fun `getById saves disclosure logs`() {
        val application = AlluDataFactory.createApplicationDto()
        every { applicationService.getApplicationById(1) } returns application

        applicationController.getById(1)

        verify { disclosureLogService.saveDisclosureLogsForApplication(application, username) }
    }

    @Test
    fun `create saves disclosure logs`() {
        val requestApplication = AlluDataFactory.createApplicationDto(id = null)
        val createdApplication = requestApplication.copy(id = 1)
        every { applicationService.create(requestApplication, username) } returns createdApplication

        applicationController.create(requestApplication)

        verify {
            disclosureLogService.saveDisclosureLogsForApplication(createdApplication, username)
        }
    }

    @Test
    fun `update saves disclosure logs`() {
        val application = AlluDataFactory.createApplicationDto(id = 1)
        every {
            applicationService.updateApplicationData(1, application.applicationData, username)
        } returns application

        applicationController.update(1, application)

        verify { disclosureLogService.saveDisclosureLogsForApplication(application, username) }
    }
}
