package fi.hel.haitaton.hanke.application

import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.factory.HankeIdsFactory
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.permissions.PermissionCode
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

private const val username = "testUser"
private const val hankeTunnus = "HAI-1234"

@ExtendWith(SpringExtension::class)
@WithMockUser(username)
class ApplicationControllerTest {

    private val applicationService: ApplicationService = mockk()
    private val hankeService: HankeService = mockk()
    private val disclosureLogService: DisclosureLogService = mockk(relaxUnitFun = true)
    private val authorizer: ApplicationAuthorizer = mockk(relaxUnitFun = true)

    private val applicationController =
        ApplicationController(
            applicationService,
            hankeService,
            disclosureLogService,
            authorizer,
        )

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun cleanUp() {
        checkUnnecessaryStub()
        confirmVerified(applicationService, disclosureLogService, authorizer)
    }

    @Test
    fun `getAll saves disclosure logs`() {
        val applications =
            listOf(
                AlluDataFactory.createApplication(hankeTunnus = hankeTunnus),
                AlluDataFactory.createApplication(
                    id = 2,
                    hankeTunnus = hankeTunnus,
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
        val hankeTunnus = "HAI-1234"
        val application = AlluDataFactory.createApplication(hankeTunnus = hankeTunnus)
        every { applicationService.getApplicationById(1) } returns application

        applicationController.getById(1)

        verifySequence {
            authorizer.authorizeApplicationId(1, PermissionCode.VIEW)
            applicationService.getApplicationById(1)
            disclosureLogService.saveDisclosureLogsForApplication(application, username)
        }
    }

    @Test
    fun `create saves disclosure logs`() {
        val hankeTunnus = "HAI-1234"
        val requestApplication =
            AlluDataFactory.createApplication(id = null, hankeTunnus = hankeTunnus)
        val createdApplication = requestApplication.copy(id = 1)
        every {
            authorizer.authorizeHankeTunnus(hankeTunnus, PermissionCode.EDIT_APPLICATIONS)
        } returns HankeIdsFactory.create(hankeTunnus = hankeTunnus)
        every { applicationService.create(requestApplication, username) } returns createdApplication

        applicationController.create(requestApplication)

        verifySequence {
            authorizer.authorizeHankeTunnus(hankeTunnus, PermissionCode.EDIT_APPLICATIONS)
            applicationService.create(requestApplication, username)
            disclosureLogService.saveDisclosureLogsForApplication(createdApplication, username)
        }
    }

    @Test
    fun `update saves disclosure logs`() {
        val hankeTunnus = "HAI-1234"
        val application = AlluDataFactory.createApplication(id = 1, hankeTunnus = hankeTunnus)
        every {
            applicationService.updateApplicationData(1, application.applicationData, username)
        } returns application

        applicationController.update(1, application)

        verifySequence {
            authorizer.authorizeApplicationId(1, PermissionCode.EDIT_APPLICATIONS)
            applicationService.updateApplicationData(1, application.applicationData, username)
            disclosureLogService.saveDisclosureLogsForApplication(application, username)
        }
    }
}
