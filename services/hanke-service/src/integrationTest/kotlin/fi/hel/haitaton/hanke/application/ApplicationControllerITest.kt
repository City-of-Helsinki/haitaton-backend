package fi.hel.haitaton.hanke.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import fi.hel.haitaton.hanke.andReturnBody
import fi.hel.haitaton.hanke.domain.HankeWithApplications
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.factory.AlluDataFactory.Companion.withContacts
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.getResourceAsBytes
import fi.hel.haitaton.hanke.permissions.PermissionCode.EDIT_APPLICATIONS
import fi.hel.haitaton.hanke.permissions.PermissionCode.VIEW
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.toJsonString
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
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
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.MediaType.APPLICATION_PDF
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private const val USERNAME = "testUser"
private const val HANKE_TUNNUS = "HAI-1234"
private const val BASE_URL = "/hakemukset"

@WebMvcTest(ApplicationController::class)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("itest")
class ApplicationControllerITest(@Autowired override val mockMvc: MockMvc) : ControllerTest {

    @Autowired private lateinit var applicationService: ApplicationService
    @Autowired private lateinit var hankeService: HankeService
    @Autowired private lateinit var permissionService: PermissionService
    @Autowired private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(applicationService, permissionService)
    }

    @Test
    fun `getAll without user ID returns 401`() {
        get(BASE_URL).andExpect(status().isUnauthorized)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `getAll with no accessible applications returns empty list`() {
        every { applicationService.getAllApplicationsForUser(USERNAME) } returns listOf()

        get(BASE_URL).andExpect(status().isOk).andExpect(content().json("[]"))

        verify { applicationService.getAllApplicationsForUser(USERNAME) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `getAll returns applications for the current user`() {
        every { applicationService.getAllApplicationsForUser(USERNAME) } returns
            AlluDataFactory.createApplications(3)

        get(BASE_URL)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(3))

        verify { applicationService.getAllApplicationsForUser(USERNAME) }
    }

    @Test
    fun `getById without user ID returns 401`() {
        get("$BASE_URL/1234").andExpect(status().isUnauthorized)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `getById with unknown ID returns 404`() {
        val id = 1234L
        every { applicationService.getApplicationById(id) } throws ApplicationNotFoundException(id)

        get("$BASE_URL/$id").andExpect(status().isNotFound)

        verify { applicationService.getApplicationById(id) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `getById with known ID returns application`() {
        val id = 1234L
        val hankeId = 42
        every { applicationService.getApplicationById(id) } returns
            AlluDataFactory.createApplication(id = id, hankeTunnus = HANKE_TUNNUS)
        every { hankeService.getHankeId(HANKE_TUNNUS) } returns hankeId
        every { permissionService.hasPermission(hankeId, USERNAME, VIEW) } returns true

        get("$BASE_URL/$id")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.applicationType").value("CABLE_REPORT"))
            .andExpect(jsonPath("$.applicationData.applicationType").value("CABLE_REPORT"))

        verify { applicationService.getApplicationById(id) }
        verify { permissionService.hasPermission(hankeId, USERNAME, VIEW) }
    }

    @Test
    fun `create without logged in user returns 401`() {
        post(BASE_URL, AlluDataFactory.createApplication(id = null))
            .andExpect(status().isUnauthorized)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `create without body returns 400`() {
        post(BASE_URL).andExpect(status().isBadRequest)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `create with proper application creates application`() {
        val hankeId = 42
        val newApplication =
            AlluDataFactory.createApplication(id = null, hankeTunnus = HANKE_TUNNUS)
        val createdApplication = newApplication.copy(id = 1234)
        every { applicationService.create(newApplication, USERNAME) } returns createdApplication
        every { hankeService.getHankeId(HANKE_TUNNUS) } returns hankeId
        every { permissionService.hasPermission(hankeId, USERNAME, EDIT_APPLICATIONS) } returns true

        val response: Application =
            post(BASE_URL, newApplication).andExpect(status().isOk).andReturnBody()

        assertEquals(createdApplication, response)
        verify { applicationService.create(newApplication, USERNAME) }
        verify { permissionService.hasPermission(hankeId, USERNAME, EDIT_APPLICATIONS) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `create with missing application data type returns 400`() {
        val application = AlluDataFactory.createApplication(id = null)
        val content: ObjectNode = OBJECT_MAPPER.valueToTree(application)
        (content.get("applicationData") as ObjectNode).remove("applicationType")

        postRaw(BASE_URL, content.toJsonString()).andExpect(status().isBadRequest)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `create with missing application type returns 400`() {
        val application = AlluDataFactory.createApplication(id = null)
        val content: ObjectNode = OBJECT_MAPPER.valueToTree(application)
        content.remove("applicationType")

        postRaw(BASE_URL, content.toJsonString())
            .andDo { print(it) }
            .andExpect(status().isBadRequest)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `create with invalid y-tunnus returns 400`() {
        val applicationData =
            AlluDataFactory.createCableReportApplicationData(
                customerWithContacts =
                    AlluDataFactory.createCompanyCustomer(registryKey = "281192-937W")
                        .withContacts()
            )
        val application =
            AlluDataFactory.createApplication(id = null, applicationData = applicationData)

        post(BASE_URL, application).andExpect(status().isBadRequest)
        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `create with hanke generation calls succeeds and returns 200`() {
        val applicationInput = AlluDataFactory.cableReportWithoutHanke()
        val mockCreatedApplication = applicationInput.toNewApplication(HANKE_TUNNUS)
        every { hankeService.generateHankeWithApplication(applicationInput, USERNAME) } returns
            HankeWithApplications(HankeFactory.create(), listOf(mockCreatedApplication))

        val response: Application =
            post("/hakemukset/johtoselvitys", applicationInput)
                .andExpect(status().isOk)
                .andReturnBody()

        assertEquals(response, mockCreatedApplication)
        verify { hankeService.generateHankeWithApplication(applicationInput, USERNAME) }
    }

    @Test
    fun `update without logged in user returns 401`() {
        put("$BASE_URL/1234", AlluDataFactory.createApplication())
            .andExpect(status().isUnauthorized)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `update without body returns 400`() {
        put("$BASE_URL/1234").andExpect(status().isBadRequest)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `update with invalid y-tunnus returns 400`() {
        val applicationData =
            AlluDataFactory.createCableReportApplicationData(
                customerWithContacts =
                    AlluDataFactory.createCompanyCustomer(registryKey = "281192-937W")
                        .withContacts()
            )
        val application =
            AlluDataFactory.createApplication(id = null, applicationData = applicationData)

        put("$BASE_URL/1234", application).andExpect(status().isBadRequest)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `update with known id returns ok`() {
        val id = 1234L
        val application = AlluDataFactory.createApplication(hankeTunnus = HANKE_TUNNUS)
        every { applicationService.getApplicationById(id) } returns application
        every {
            applicationService.updateApplicationData(id, application.applicationData, USERNAME)
        } returns application
        every { hankeService.getHankeId(HANKE_TUNNUS) } returns 42
        every { permissionService.hasPermission(42, USERNAME, EDIT_APPLICATIONS) } returns true

        val response: Application =
            put("$BASE_URL/$id", application).andExpect(status().isOk).andReturnBody()

        assertEquals(application, response)
        verify {
            applicationService.updateApplicationData(id, application.applicationData, USERNAME)
        }
        verify { applicationService.getApplicationById(id) }
        verify { permissionService.hasPermission(42, USERNAME, EDIT_APPLICATIONS) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `update with missing application data type returns 400`() {
        val application = AlluDataFactory.createApplication()
        val content: ObjectNode = OBJECT_MAPPER.valueToTree(application)
        (content.get("applicationData") as ObjectNode).remove("applicationType")

        putRaw("$BASE_URL/1234", content.toJsonString()).andExpect(status().isBadRequest)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `update with missing application type returns 400`() {
        val application = AlluDataFactory.createApplication()
        val content: ObjectNode = OBJECT_MAPPER.valueToTree(application)
        content.remove("applicationType")

        putRaw("$BASE_URL/1234", content.toJsonString())
            .andDo { print(it) }
            .andExpect(status().isBadRequest)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `update with unknown id returns 404`() {
        val id = 1234L
        val application = AlluDataFactory.createApplication(hankeTunnus = HANKE_TUNNUS)
        every { applicationService.getApplicationById(id) } returns application
        every {
            applicationService.updateApplicationData(id, application.applicationData, USERNAME)
        } throws ApplicationNotFoundException(id)
        every { hankeService.getHankeId(HANKE_TUNNUS) } returns 42
        every { permissionService.hasPermission(42, USERNAME, EDIT_APPLICATIONS) } returns true

        put("$BASE_URL/$id", application).andExpect(status().isNotFound)

        verify { applicationService.getApplicationById(id) }
        verify {
            applicationService.updateApplicationData(id, application.applicationData, USERNAME)
        }
        verify { permissionService.hasPermission(42, USERNAME, EDIT_APPLICATIONS) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `update with application that's no longer pending returns 409`() {
        val id = 1234L
        val application = AlluDataFactory.createApplication(hankeTunnus = HANKE_TUNNUS)
        every { applicationService.getApplicationById(id) } returns application
        every {
            applicationService.updateApplicationData(id, application.applicationData, USERNAME)
        } throws ApplicationAlreadyProcessingException(id, 21)
        every { hankeService.getHankeId(HANKE_TUNNUS) } returns 42
        every { permissionService.hasPermission(42, USERNAME, EDIT_APPLICATIONS) } returns true

        put("$BASE_URL/$id", application).andExpect(status().isConflict)

        verify { applicationService.getApplicationById(id) }
        verify {
            applicationService.updateApplicationData(id, application.applicationData, USERNAME)
        }
        verify { permissionService.hasPermission(42, USERNAME, EDIT_APPLICATIONS) }
    }

    @Test
    fun `sendApplication without logged in user returns 401`() {
        post("$BASE_URL/1234/send-application").andExpect(status().isUnauthorized)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `sendApplication without body sends application to Allu and returns the result`() {
        val id = 1234L
        val application = AlluDataFactory.createApplication(hankeTunnus = HANKE_TUNNUS)
        every { hankeService.getHankeId(HANKE_TUNNUS) } returns 42
        every { applicationService.getApplicationById(id) } returns application
        every { applicationService.sendApplication(id, USERNAME) } returns application
        every { permissionService.hasPermission(42, USERNAME, EDIT_APPLICATIONS) } returns true

        val response: Application =
            post("$BASE_URL/$id/send-application").andExpect(status().isOk).andReturnBody()

        assertEquals(application, response)
        verify { applicationService.getApplicationById(id) }
        verify { applicationService.sendApplication(id, USERNAME) }
        verify { permissionService.hasPermission(42, USERNAME, EDIT_APPLICATIONS) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `sendApplication ignores request body`() {
        val id = 1234L
        val application = AlluDataFactory.createApplication(id = id, alluid = 21)
        every { hankeService.getHankeId(HANKE_TUNNUS) } returns 42
        every { permissionService.hasPermission(42, USERNAME, EDIT_APPLICATIONS) } returns true
        every { applicationService.getApplicationById(id) } returns application
        every { applicationService.sendApplication(id, USERNAME) } returns application

        val response: Application =
            post("$BASE_URL/$id/send-application", application.copy(alluid = 9999))
                .andExpect(status().isOk)
                .andReturnBody()

        assertEquals(application, response)
        verify { applicationService.getApplicationById(id) }
        verify { permissionService.hasPermission(42, USERNAME, EDIT_APPLICATIONS) }
        verify { applicationService.sendApplication(id, USERNAME) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `sendApplication ignores even broken request body`() {
        val id = 1234L
        val application = AlluDataFactory.createApplication()
        val content: ObjectNode = OBJECT_MAPPER.valueToTree(application)
        (content.get("applicationData") as ObjectNode).remove("applicationType")
        every { hankeService.getHankeId(HANKE_TUNNUS) } returns 42
        every { permissionService.hasPermission(42, USERNAME, EDIT_APPLICATIONS) } returns true
        every { applicationService.getApplicationById(id) } returns application
        every { applicationService.sendApplication(id, USERNAME) } returns application

        val response: Application =
            postRaw("$BASE_URL/$id/send-application", content.toJsonString())
                .andExpect(status().isOk)
                .andReturnBody()

        assertEquals(application, response)
        verify { applicationService.getApplicationById(id) }
        verify { permissionService.hasPermission(42, USERNAME, EDIT_APPLICATIONS) }
        verify { applicationService.sendApplication(id, USERNAME) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `sendApplication with unknown id returns 404`() {
        val id = 1234L
        every { applicationService.getApplicationById(id) } throws ApplicationNotFoundException(id)

        post("$BASE_URL/$id/send-application").andExpect(status().isNotFound)

        verify { applicationService.getApplicationById(id) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `sendApplication with application that's no longer pending returns 409`() {
        val id = 1234L
        every { hankeService.getHankeId(HANKE_TUNNUS) } returns 42
        every { permissionService.hasPermission(42, USERNAME, EDIT_APPLICATIONS) } returns true
        every { applicationService.getApplicationById(id) } returns
            AlluDataFactory.createApplication(id = id, hankeTunnus = HANKE_TUNNUS)
        every { applicationService.sendApplication(id, USERNAME) } throws
            ApplicationAlreadyProcessingException(id, 21)

        post("$BASE_URL/$id/send-application").andExpect(status().isConflict)

        verify { applicationService.getApplicationById(id) }
        verify { permissionService.hasPermission(42, USERNAME, EDIT_APPLICATIONS) }
        verify { applicationService.sendApplication(id, USERNAME) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `sendApplication with invalid application data returns 409`() {
        val id = 1234L
        every { hankeService.getHankeId(HANKE_TUNNUS) } returns 42
        every { permissionService.hasPermission(42, USERNAME, EDIT_APPLICATIONS) } returns true
        every { applicationService.getApplicationById(id) } returns
            AlluDataFactory.createApplication(id = id, hankeTunnus = HANKE_TUNNUS)
        every { applicationService.sendApplication(id, USERNAME) } throws
            AlluDataException("applicationData.some.path", AlluDataError.EMPTY_OR_NULL)

        post("$BASE_URL/$id/send-application").andExpect(status().isConflict)

        verify { permissionService.hasPermission(42, USERNAME, EDIT_APPLICATIONS) }
        verify { applicationService.getApplicationById(id) }
        verify { applicationService.sendApplication(id, USERNAME) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `sendApplication without hanke permissions is not allowed`() {
        val id = 11L
        every { hankeService.getHankeId(any()) } returns 42
        every { applicationService.getApplicationById(id) } returns
            AlluDataFactory.createApplication(id = id, hankeTunnus = HANKE_TUNNUS)
        every { permissionService.hasPermission(42, USERNAME, EDIT_APPLICATIONS) } returns false

        post("$BASE_URL/$id/send-application").andExpect(status().isNotFound)

        verify { applicationService.getApplicationById(id) }
        verify { permissionService.hasPermission(42, USERNAME, EDIT_APPLICATIONS) }
    }

    @Test
    fun `delete without user ID returns 401`() {
        delete("$BASE_URL/1").andExpect(status().isUnauthorized)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `delete with unknown id returns 404`() {
        val id = 1234L
        every { applicationService.getApplicationById(id) } throws ApplicationNotFoundException(id)

        delete("$BASE_URL/$id").andExpect(status().isNotFound)

        verify { applicationService.getApplicationById(id) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `delete with known id deletes application`() {
        val id = 1234L
        every { hankeService.getHankeId(any()) } returns 42
        every { permissionService.hasPermission(42, USERNAME, EDIT_APPLICATIONS) } returns true
        every { applicationService.getApplicationById(id) } returns
            AlluDataFactory.createApplication(id = id, hankeTunnus = HANKE_TUNNUS)
        justRun { applicationService.delete(id, USERNAME) }

        delete("$BASE_URL/$id").andExpect(status().isOk).andExpect(content().string(""))

        verify { permissionService.hasPermission(42, USERNAME, EDIT_APPLICATIONS) }
        verify { applicationService.getApplicationById(id) }
        verify { applicationService.delete(id, USERNAME) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `delete with non-pending application in allu returns 409 Conflict`() {
        val id = 1234L
        every { hankeService.getHankeId(any()) } returns 42
        every { permissionService.hasPermission(42, USERNAME, EDIT_APPLICATIONS) } returns true
        every { applicationService.getApplicationById(id) } returns
            AlluDataFactory.createApplication(id = id, hankeTunnus = HANKE_TUNNUS)
        every { applicationService.delete(id, USERNAME) } throws
            ApplicationAlreadyProcessingException(id, 41)

        delete("$BASE_URL/$id").andExpect(status().isConflict)

        verify { applicationService.getApplicationById(id) }
        verify { permissionService.hasPermission(42, USERNAME, EDIT_APPLICATIONS) }
        verify { applicationService.delete(id, USERNAME) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `delete without hanke permissions is not allowed`() {
        val id = 11L
        every { hankeService.getHankeId(any()) } returns 42
        every { applicationService.getApplicationById(id) } returns
            AlluDataFactory.createApplication(id = id, hankeTunnus = HANKE_TUNNUS)
        every { permissionService.hasPermission(42, USERNAME, EDIT_APPLICATIONS) } returns false

        delete("$BASE_URL/11").andExpect(status().isNotFound)

        verify { applicationService.getApplicationById(id) }
        verify { permissionService.hasPermission(42, USERNAME, EDIT_APPLICATIONS) }
    }

    @Test
    fun `downloadDecision without user returns 401`() {
        get("$BASE_URL/1/paatos").andExpect(status().isUnauthorized)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `downloadDecision with unknown ID returns 404`() {
        val id = 11L
        every { applicationService.getApplicationById(id) } throws ApplicationNotFoundException(id)

        get("$BASE_URL/$id/paatos", APPLICATION_PDF, APPLICATION_JSON)
            .andExpect(status().isNotFound)
            .andExpect(content().contentType(APPLICATION_JSON))
            .andExpect(jsonPath("errorCode").value("HAI2001"))
            .andExpect(jsonPath("errorMessage").value("Application not found"))

        verify { applicationService.getApplicationById(id) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `downloadDecision when application has no decision returns 404`() {
        val id = 11L
        every { hankeService.getHankeId(any()) } returns 42
        every { permissionService.hasPermission(42, USERNAME, VIEW) } returns true
        every { applicationService.getApplicationById(id) } returns
            AlluDataFactory.createApplication(id = id, hankeTunnus = HANKE_TUNNUS)
        every { applicationService.downloadDecision(id, USERNAME) } throws
            ApplicationDecisionNotFoundException("Decision not found in Allu. alluid=23")

        get("$BASE_URL/11/paatos", APPLICATION_PDF, APPLICATION_JSON)
            .andExpect(status().isNotFound)
            .andExpect(content().contentType(APPLICATION_JSON))
            .andExpect(jsonPath("errorCode").value("HAI2006"))
            .andExpect(jsonPath("errorMessage").value("Application decision not found"))

        verify { applicationService.getApplicationById(id) }
        verify { permissionService.hasPermission(42, USERNAME, VIEW) }
        verify { applicationService.downloadDecision(id, USERNAME) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `downloadDecision with known id returns bytes and correct headers`() {
        every { hankeService.getHankeId(HANKE_TUNNUS) } returns 42
        every { permissionService.hasPermission(42, USERNAME, VIEW) } returns true
        every { applicationService.getApplicationById(11) } returns
            AlluDataFactory.createApplication(id = 11, hankeTunnus = HANKE_TUNNUS)
        val pdfBytes = "/fi/hel/haitaton/hanke/decision/fake-decision.pdf".getResourceAsBytes()
        every { applicationService.downloadDecision(11, USERNAME) } returns
            Pair("JS230001", pdfBytes)

        get("$BASE_URL/11/paatos", APPLICATION_PDF, APPLICATION_JSON)
            .andExpect(status().isOk)
            .andExpect(header().string("Content-Disposition", "inline; filename=JS230001.pdf"))
            .andExpect(content().contentType(APPLICATION_PDF))
            .andExpect(content().bytes(pdfBytes))

        verify { applicationService.getApplicationById(11) }
        verify { permissionService.hasPermission(42, USERNAME, VIEW) }
        verify { applicationService.downloadDecision(11, USERNAME) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `downloadDecision without hanke permissions is not allowed`() {
        val id = 11L
        every { hankeService.getHankeId(any()) } returns 42
        every { applicationService.getApplicationById(id) } returns
            AlluDataFactory.createApplication(id = id, hankeTunnus = HANKE_TUNNUS)
        every { permissionService.hasPermission(42, USERNAME, VIEW) } returns false

        get("$BASE_URL/$id/paatos", APPLICATION_PDF, APPLICATION_JSON)
            .andExpect(status().isNotFound)

        verify { applicationService.getApplicationById(id) }
        verify { permissionService.hasPermission(42, USERNAME, VIEW) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `Application and hanke can be linked`() {
        val id = 1234L
        every { applicationService.getApplicationById(id) } returns
            AlluDataFactory.createApplication(id = id, hankeTunnus = HANKE_TUNNUS)
        every { hankeService.getHankeId(HANKE_TUNNUS) } returns 42
        every { permissionService.hasPermission(42, USERNAME, VIEW) } returns true

        get("$BASE_URL/$id")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.hankeTunnus").value(HANKE_TUNNUS))

        verify { applicationService.getApplicationById(id) }
        verify { permissionService.hasPermission(42, USERNAME, VIEW) }
    }

    @Test
    @WithMockUser(USERNAME)
    fun `Creating an application without hankeTunnus fails`() {
        val newApplication =
            AlluDataFactory.createApplication(id = null, hankeTunnus = HANKE_TUNNUS)
        val json = objectMapper.valueToTree<ObjectNode>(newApplication)
        json.remove("hankeTunnus")
        val text = json.asText()
        postRaw(BASE_URL, text).andExpect(status().isBadRequest)
    }

    @Test
    @WithMockUser(USERNAME)
    fun `Creating an application without hanke permissions fails`() {
        val newApplication =
            AlluDataFactory.createApplication(id = null, hankeTunnus = HANKE_TUNNUS)
        every { hankeService.getHankeId(HANKE_TUNNUS) } returns 42
        every { permissionService.hasPermission(42, USERNAME, EDIT_APPLICATIONS) } returns false

        post(BASE_URL, newApplication).andExpect(status().isNotFound)

        verify { permissionService.hasPermission(42, USERNAME, EDIT_APPLICATIONS) }
    }
}
