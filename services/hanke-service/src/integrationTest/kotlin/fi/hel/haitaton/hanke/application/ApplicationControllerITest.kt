package fi.hel.haitaton.hanke.application

import com.fasterxml.jackson.databind.node.ObjectNode
import fi.hel.haitaton.hanke.*
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.getResourceAsBytes
import fi.hel.haitaton.hanke.toJsonString
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private const val username = "testUser"

@WebMvcTest(ApplicationController::class)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("itest")
class ApplicationControllerITest(@Autowired override val mockMvc: MockMvc) : ControllerTest {

    @Autowired private lateinit var applicationService: ApplicationService
    @Autowired private lateinit var hankeService: HankeService
    @Autowired private lateinit var permissionService: PermissionService

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        // TODO: Needs newer MockK, which needs newer Spring test dependencies
        // checkUnnecessaryStub()
        confirmVerified(applicationService, hankeService, permissionService)
    }

    @Test
    fun `getAll without user ID returns 401`() {
        get("/hakemukset").andExpect(status().isUnauthorized)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(username)
    fun `getAll with no accessible applications returns empty list`() {
        every { applicationService.getAllApplicationsForUser(username) } returns listOf()

        get("/hakemukset").andExpect(status().isOk).andExpect(content().json("[]"))

        verify { applicationService.getAllApplicationsForUser(username) }
    }

    @Test
    @WithMockUser(username)
    fun `getAll returns applications for the current user`() {
        every { applicationService.getAllApplicationsForUser(username) } returns
            AlluDataFactory.createApplications(3)

        get("/hakemukset")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(3))

        verify { applicationService.getAllApplicationsForUser(username) }
    }

    @Test
    fun `getById without user ID returns 401`() {
        get("/hakemukset/1234").andExpect(status().isUnauthorized)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(username)
    fun `getById with unknown ID returns 404`() {
        every { applicationService.getApplicationById(1234, username) } throws
            ApplicationNotFoundException(1234)

        get("/hakemukset/1234").andExpect(status().isNotFound)

        verify { applicationService.getApplicationById(1234, username) }
    }

    @Test
    @WithMockUser(username)
    fun `getById with known ID returns application`() {
        val hankeTunnus = "HAI-1234"
        every { applicationService.getApplicationById(1234, username) } returns
            AlluDataFactory.createApplication(id = 1234, hankeTunnus = hankeTunnus)
        every { hankeService.getHankeId(hankeTunnus) } returns 42
        every { permissionService.hasPermission(42, any(), any()) } returns true

        get("/hakemukset/1234")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.applicationType").value("CABLE_REPORT"))
            .andExpect(jsonPath("$.applicationData.applicationType").value("CABLE_REPORT"))

        verify { applicationService.getApplicationById(1234, username) }
        verify { hankeService.getHankeId(hankeTunnus) }
        verify { permissionService.hasPermission(42, any(), any()) }
    }

    @Test
    fun `create without logged in user returns 401`() {
        post("/hakemukset", AlluDataFactory.createApplication(id = null))
            .andExpect(status().isUnauthorized)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(username)
    fun `create without body returns 400`() {
        post("/hakemukset").andExpect(status().isBadRequest)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(username)
    fun `create with proper application creates application`() {
        val hankeTunnus = "HAI-1234"
        val newApplication = AlluDataFactory.createApplication(id = null, hankeTunnus = hankeTunnus)
        val createdApplication = newApplication.copy(id = 1234)
        every { applicationService.create(newApplication, username) } returns createdApplication
        every { hankeService.getHankeId(hankeTunnus) } returns 42
        every { permissionService.hasPermission(42, any(), any()) } returns true

        val response: Application =
            post("/hakemukset", newApplication).andExpect(status().isOk).andReturnBody()

        assertEquals(createdApplication, response)
        verify { applicationService.create(newApplication, username) }
        verify { hankeService.getHankeId(hankeTunnus) }
        verify { permissionService.hasPermission(42, any(), any()) }
    }

    @Test
    @WithMockUser(username)
    fun `create with missing application data type returns 400`() {
        val application = AlluDataFactory.createApplication(id = null)
        val content: ObjectNode = OBJECT_MAPPER.valueToTree(application)
        (content.get("applicationData") as ObjectNode).remove("applicationType")

        postRaw("/hakemukset", content.toJsonString()).andExpect(status().isBadRequest)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(username)
    fun `create with missing application type returns 400`() {
        val application = AlluDataFactory.createApplication(id = null)
        val content: ObjectNode = OBJECT_MAPPER.valueToTree(application)
        content.remove("applicationType")

        postRaw("/hakemukset", content.toJsonString())
            .andDo { print(it) }
            .andExpect(status().isBadRequest)

        verify { applicationService wasNot Called }
    }

    @Test
    fun `update without logged in user returns 401`() {
        put("/hakemukset/1234", AlluDataFactory.createApplication())
            .andExpect(status().isUnauthorized)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(username)
    fun `update without body returns 400`() {
        put("/hakemukset/1234").andExpect(status().isBadRequest)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(username)
    fun `update with known id returns ok`() {
        val hankeTunnus = "HAI-1234"
        val application = AlluDataFactory.createApplication(hankeTunnus = hankeTunnus)
        every {
            applicationService.updateApplicationData(1234, application.applicationData, username)
        } returns application
        every { hankeService.getHankeId(hankeTunnus) } returns 42
        every { permissionService.hasPermission(42, any(), any()) } returns true

        val response: Application =
            put("/hakemukset/1234", application).andExpect(status().isOk).andReturnBody()

        assertEquals(application, response)
        verify {
            applicationService.updateApplicationData(1234, application.applicationData, username)
        }
        verify { hankeService.getHankeId(hankeTunnus) }
        verify { permissionService.hasPermission(42, any(), any()) }
    }

    @Test
    @WithMockUser(username)
    fun `update with missing application data type returns 400`() {
        val application = AlluDataFactory.createApplication()
        val content: ObjectNode = OBJECT_MAPPER.valueToTree(application)
        (content.get("applicationData") as ObjectNode).remove("applicationType")

        putRaw("/hakemukset/1234", content.toJsonString()).andExpect(status().isBadRequest)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(username)
    fun `update with missing application type returns 400`() {
        val application = AlluDataFactory.createApplication()
        val content: ObjectNode = OBJECT_MAPPER.valueToTree(application)
        content.remove("applicationType")

        putRaw("/hakemukset/1234", content.toJsonString())
            .andDo { print(it) }
            .andExpect(status().isBadRequest)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(username)
    fun `update with unknown id returns 404`() {
        val hankeTunnus = "HAI-1234"
        val application = AlluDataFactory.createApplication(hankeTunnus = hankeTunnus)
        every {
            applicationService.updateApplicationData(1234, application.applicationData, username)
        } throws ApplicationNotFoundException(1234)
        every { hankeService.getHankeId(hankeTunnus) } returns 42
        every { permissionService.hasPermission(42, any(), any()) } returns true

        put("/hakemukset/1234", application).andExpect(status().isNotFound)

        verify {
            applicationService.updateApplicationData(1234, application.applicationData, username)
        }
        verify { hankeService.getHankeId(hankeTunnus) }
        verify { permissionService.hasPermission(42, any(), any()) }
    }

    @Test
    @WithMockUser(username)
    fun `update with application that's no longer pending returns 409`() {
        val hankeTunnus = "HAI-1234"
        val application = AlluDataFactory.createApplication(hankeTunnus = hankeTunnus)
        every {
            applicationService.updateApplicationData(1234, application.applicationData, username)
        } throws ApplicationAlreadyProcessingException(1234, 21)
        every { hankeService.getHankeId(hankeTunnus) } returns 42
        every { permissionService.hasPermission(42, any(), any()) } returns true

        put("/hakemukset/1234", application).andExpect(status().isConflict)

        verify {
            applicationService.updateApplicationData(1234, application.applicationData, username)
        }
        verify { hankeService.getHankeId(hankeTunnus) }
        verify { permissionService.hasPermission(42, any(), any()) }
    }

    @Test
    fun `sendApplication without logged in user returns 401`() {
        post("/hakemukset/1234/send-application").andExpect(status().isUnauthorized)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(username)
    fun `sendApplication without body sends application to Allu and returns the result`() {
        val application = AlluDataFactory.createApplication()
        every { applicationService.sendApplication(1234, username) } returns application

        val response: Application =
            post("/hakemukset/1234/send-application").andExpect(status().isOk).andReturnBody()

        assertEquals(application, response)
        verify { applicationService.sendApplication(1234, username) }
    }

    @Test
    @WithMockUser(username)
    fun `sendApplication ignores request body`() {
        val application = AlluDataFactory.createApplication(alluid = 21)
        every { applicationService.sendApplication(1234, username) } returns application

        val response: Application =
            post("/hakemukset/1234/send-application", application.copy(alluid = 9999))
                .andExpect(status().isOk)
                .andReturnBody()

        assertEquals(application, response)
        verify { applicationService.sendApplication(1234, username) }
    }

    @Test
    @WithMockUser(username)
    fun `sendApplication ignores even broken request body`() {
        val application = AlluDataFactory.createApplication()
        val content: ObjectNode = OBJECT_MAPPER.valueToTree(application)
        (content.get("applicationData") as ObjectNode).remove("applicationType")
        every { applicationService.sendApplication(1234, username) } returns application

        val response: Application =
            postRaw("/hakemukset/1234/send-application", content.toJsonString())
                .andExpect(status().isOk)
                .andReturnBody()

        assertEquals(application, response)
        verify { applicationService.sendApplication(1234, username) }
    }

    @Test
    @WithMockUser(username)
    fun `sendApplication with unknown id returns 404`() {
        every { applicationService.sendApplication(1234, username) } throws
            ApplicationNotFoundException(1234)

        post("/hakemukset/1234/send-application").andExpect(status().isNotFound)

        verify { applicationService.sendApplication(1234, username) }
    }

    @Test
    @WithMockUser(username)
    fun `sendApplication with application that's no longer pending returns 409`() {
        every { applicationService.sendApplication(1234, username) } throws
            ApplicationAlreadyProcessingException(1234, 21)

        post("/hakemukset/1234/send-application").andExpect(status().isConflict)

        verify { applicationService.sendApplication(1234, username) }
    }

    @Test
    @WithMockUser(username)
    fun `Application can be linked to a hanke`() {
        val hankeTunnus = "HAI-1234"
        every { applicationService.getApplicationById(1234, username) } returns
            AlluDataFactory.createApplication(id = 1234, hankeTunnus = hankeTunnus)
        every { hankeService.getHankeId(hankeTunnus) } returns 42
        every { permissionService.hasPermission(42, any(), any()) } returns true

        get("/hakemukset/1234")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.hankeTunnus").value(hankeTunnus))

        verify { applicationService.getApplicationById(1234, username) }
        verify { hankeService.getHankeId(hankeTunnus) }
        verify { permissionService.hasPermission(42, any(), any()) }
    }

    @Test
    @WithMockUser(username)
    fun `Creating an application without hanke permissions fails`() {
        val hankeTunnus = "HAI-1234"
        every { applicationService.getApplicationById(1234, username) } returns
            AlluDataFactory.createApplication(id = 1234, hankeTunnus = hankeTunnus)
        every { hankeService.getHankeId(hankeTunnus) } returns 42
        every { permissionService.hasPermission(42, any(), any()) } returns false

        get("/hakemukset/1234").andExpect(status().isNotFound)

        verify { applicationService.getApplicationById(1234, username) }
        verify { hankeService.getHankeId(hankeTunnus) }
        verify { permissionService.hasPermission(42, any(), any()) }
    }

    @Test
    @WithMockUser(username)
    fun `sendApplication with invalid application data returns 409`() {
        every { applicationService.sendApplication(1234, username) } throws
            AlluDataException("applicationData.some.path", AlluDataError.EMPTY_OR_NULL)

        post("/hakemukset/1234/send-application").andExpect(status().isConflict)

        verify { applicationService.sendApplication(1234, username) }
    }

    @Test
    fun `delete without user ID returns 401`() {
        delete("/hakemukset/1").andExpect(status().isUnauthorized)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(username)
    fun `delete with unknown id returns 404`() {
        every { applicationService.delete(1234, username) } throws
            ApplicationNotFoundException(1234)

        delete("/hakemukset/1234").andExpect(status().isNotFound)

        verify { applicationService.delete(1234, username) }
    }

    @Test
    @WithMockUser(username)
    fun `delete with known id deletes application`() {
        justRun { applicationService.delete(1234, username) }

        delete("/hakemukset/1234").andExpect(status().isOk).andExpect(content().string(""))

        verify { applicationService.delete(1234, username) }
    }

    @Test
    @WithMockUser(username)
    fun `delete with non-pending application in allu returns 409 Conflict`() {
        every { applicationService.delete(1234, username) } throws
            ApplicationAlreadyProcessingException(1234, 41)

        delete("/hakemukset/1234").andExpect(status().isConflict)

        verify { applicationService.delete(1234, username) }
    }

    @Test
    fun `downloadDecision without user returns 401`() {
        get("/hakemukset/1/paatos").andExpect(status().isUnauthorized)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(username)
    fun `downloadDecision with unknown ID returns 404`() {
        every { applicationService.downloadDecision(11, username) } throws
            ApplicationNotFoundException(11)

        get("/hakemukset/11/paatos", MediaType.APPLICATION_PDF, MediaType.APPLICATION_JSON)
            .andExpect(status().isNotFound)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("errorCode").value("HAI2001"))
            .andExpect(jsonPath("errorMessage").value("Application not found"))

        verify { applicationService.downloadDecision(11, username) }
    }

    @Test
    @WithMockUser(username)
    fun `downloadDecision when application has no decision returns 404`() {
        every { applicationService.downloadDecision(11, username) } throws
            ApplicationDecisionNotFoundException("Decision not found in Allu. alluid=23")

        get("/hakemukset/11/paatos", MediaType.APPLICATION_PDF, MediaType.APPLICATION_JSON)
            .andExpect(status().isNotFound)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("errorCode").value("HAI2006"))
            .andExpect(jsonPath("errorMessage").value("Application decision not found"))

        verify { applicationService.downloadDecision(11, username) }
    }

    @Test
    @WithMockUser(username)
    fun `downloadDecision with known id returns bytes and correct headers`() {
        val pdfBytes = "/fi/hel/haitaton/hanke/decision/fake-decision.pdf".getResourceAsBytes()
        every { applicationService.downloadDecision(11, username) } returns
            Pair("JS230001", pdfBytes)

        get("/hakemukset/11/paatos", MediaType.APPLICATION_PDF, MediaType.APPLICATION_JSON)
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Disposition", "inline; filename=JS230001.pdf"))
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(content().bytes(pdfBytes))

        verify { applicationService.downloadDecision(11, username) }
    }
}
