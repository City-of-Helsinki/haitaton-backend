package fi.hel.haitaton.hanke.application

import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionService
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
private const val hankeTunnus = "HAI-1234"

@ExtendWith(SpringExtension::class)
@WithMockUser(username)
class ApplicationControllerTest {

    private val applicationService: ApplicationService = mockk()
    private val hankeService: HankeService = mockk()
    private val disclosureLogService: DisclosureLogService = mockk(relaxUnitFun = true)
    private val permissionService: PermissionService = mockk()

    private val applicationController =
        ApplicationController(
            applicationService,
            hankeService,
            disclosureLogService,
            permissionService
        )

    @AfterEach
    fun cleanUp() {
        // TODO: Needs newer MockK, which needs newer Spring test dependencies
        // checkUnnecessaryStub()
        confirmVerified(applicationService, disclosureLogService, permissionService)
        clearAllMocks()
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
        every { hankeService.getHankeId(hankeTunnus) } returns 42
        every { permissionService.hasPermission(42, username, PermissionCode.VIEW) } returns true
        applicationController.getById(1)

        verify { permissionService.hasPermission(42, username, PermissionCode.VIEW) }
        verify {
            disclosureLogService.saveDisclosureLogsForApplication(application, username)
            applicationService.getApplicationById(1)
        }
    }

    @Test
    fun `create saves disclosure logs`() {
        val hankeTunnus = "HAI-1234"
        val requestApplication =
            AlluDataFactory.createApplication(id = null, hankeTunnus = hankeTunnus)
        val createdApplication = requestApplication.copy(id = 1)
        every { hankeService.getHankeId(hankeTunnus) } returns 42
        every {
            permissionService.hasPermission(42, username, PermissionCode.EDIT_APPLICATIONS)
        } returns true
        every { applicationService.create(requestApplication, username) } returns createdApplication

        applicationController.create(false, requestApplication)

        verify { permissionService.hasPermission(42, username, PermissionCode.EDIT_APPLICATIONS) }
        verify {
            disclosureLogService.saveDisclosureLogsForApplication(createdApplication, username)
            applicationService.create(requestApplication, username)
        }
    }

    @Test
    fun `update saves disclosure logs`() {
        val hankeTunnus = "HAI-1234"
        val application = AlluDataFactory.createApplication(id = 1, hankeTunnus = hankeTunnus)
        every {
            applicationService.updateApplicationData(1, application.applicationData, username)
        } returns application
        every { applicationService.getApplicationById(1) } returns application
        every { hankeService.getHankeId(hankeTunnus) } returns 42
        every {
            permissionService.hasPermission(42, username, PermissionCode.EDIT_APPLICATIONS)
        } returns true

        applicationController.update(1, application)

        verify { applicationService.getApplicationById(1) }
        verify { permissionService.hasPermission(42, username, PermissionCode.EDIT_APPLICATIONS) }
        verify {
            disclosureLogService.saveDisclosureLogsForApplication(application, username)
            applicationService.updateApplicationData(1, application.applicationData, username)
        }
    }
}
