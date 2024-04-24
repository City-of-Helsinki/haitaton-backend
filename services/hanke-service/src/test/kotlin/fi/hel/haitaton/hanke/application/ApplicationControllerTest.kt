package fi.hel.haitaton.hanke.application

import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.test.USERNAME
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.junit.jupiter.SpringExtension

private const val hankeTunnus = "HAI-1234"

@ExtendWith(SpringExtension::class)
@WithMockUser(USERNAME)
class ApplicationControllerTest {

    private val applicationService: ApplicationService = mockk()
    private val hankeService: HankeService = mockk()
    private val disclosureLogService: DisclosureLogService = mockk(relaxUnitFun = true)

    private val applicationController =
        ApplicationController(
            applicationService,
            hankeService,
            disclosureLogService,
        )

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun cleanUp() {
        checkUnnecessaryStub()
        confirmVerified(applicationService, disclosureLogService)
    }

    @Test
    fun `getAll saves disclosure logs`() {
        val applications =
            listOf(
                ApplicationFactory.createApplication(hankeTunnus = hankeTunnus),
                ApplicationFactory.createApplication(
                    id = 2,
                    hankeTunnus = hankeTunnus,
                    applicationData =
                        ApplicationFactory.createCableReportApplicationData(
                            name = "Toinen selvitys"
                        )
                ),
            )
        every { applicationService.getAllApplicationsForUser(USERNAME) } returns applications

        applicationController.getAll()

        verify {
            disclosureLogService.saveDisclosureLogsForApplications(applications, USERNAME)
            applicationService.getAllApplicationsForUser(USERNAME)
        }
    }

    @Test
    fun `getById saves disclosure logs`() {
        val hankeTunnus = "HAI-1234"
        val application = ApplicationFactory.createApplication(hankeTunnus = hankeTunnus)
        every { applicationService.getApplicationById(1) } returns application

        applicationController.getById(1)

        verifySequence {
            applicationService.getApplicationById(1)
            disclosureLogService.saveDisclosureLogsForApplication(application, USERNAME)
        }
    }

    @Test
    fun `create saves disclosure logs`() {
        val hankeTunnus = "HAI-1234"
        val requestApplication =
            ApplicationFactory.createApplication(id = 0, hankeTunnus = hankeTunnus)
        val createdApplication = requestApplication.copy(id = 1)
        every { applicationService.create(requestApplication, USERNAME) } returns createdApplication

        applicationController.create(requestApplication)

        verifySequence {
            applicationService.create(requestApplication, USERNAME)
            disclosureLogService.saveDisclosureLogsForApplication(createdApplication, USERNAME)
        }
    }

    @Test
    fun `update saves disclosure logs`() {
        val hankeTunnus = "HAI-1234"
        val application = ApplicationFactory.createApplication(id = 1, hankeTunnus = hankeTunnus)
        every {
            applicationService.updateApplicationData(1, application.applicationData, USERNAME)
        } returns application

        applicationController.update(1, application)

        verifySequence {
            applicationService.updateApplicationData(1, application.applicationData, USERNAME)
            disclosureLogService.saveDisclosureLogsForApplication(application, USERNAME)
        }
    }
}
