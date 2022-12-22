package fi.hel.haitaton.hanke.allu

import com.fasterxml.jackson.databind.node.ObjectNode
import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import fi.hel.haitaton.hanke.andReturnBody
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.toJsonString
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private const val userName = "testUser"

@WebMvcTest(ApplicationController::class)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("itest")
class ApplicationControllerITest(@Autowired override val mockMvc: MockMvc) : ControllerTest {

    @Autowired private lateinit var applicationService: ApplicationService

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        // TODO: Needs newer MockK, which needs newer Spring test dependencies
        // checkUnnecessaryStub()
        confirmVerified(applicationService)
    }

    @Test
    fun `getAll without user ID returns 401`() {
        get("/hakemukset").andExpect(status().isUnauthorized)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(userName)
    fun `getAll with no accessible applications returns empty list`() {
        every { applicationService.getAllApplicationsForUser(userName) } returns listOf()

        get("/hakemukset").andExpect(status().isOk).andExpect(content().json("[]"))

        verify { applicationService.getAllApplicationsForUser(userName) }
    }

    @Test
    @WithMockUser(userName)
    fun `getAll returns applications for the current user`() {
        every { applicationService.getAllApplicationsForUser(userName) } returns
            AlluDataFactory.createApplications(3)

        get("/hakemukset")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(3))

        verify { applicationService.getAllApplicationsForUser(userName) }
    }

    @Test
    fun `getById without user ID returns 401`() {
        get("/hakemukset/1234").andExpect(status().isUnauthorized)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(userName)
    fun `getById with unknown ID returns 404`() {
        every { applicationService.getApplicationById(1234, userName) } throws
            ApplicationNotFoundException(1234)

        get("/hakemukset/1234").andExpect(status().isNotFound)

        verify { applicationService.getApplicationById(1234, userName) }
    }

    @Test
    @WithMockUser(userName)
    fun `getById with known ID returns application`() {
        every { applicationService.getApplicationById(1234, userName) } returns
            AlluDataFactory.createApplication(id = 1234)

        get("/hakemukset/1234")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.applicationType").value("CABLE_REPORT"))
            .andExpect(jsonPath("$.applicationData.applicationType").value("CABLE_REPORT"))

        verify { applicationService.getApplicationById(1234, userName) }
    }

    @Test
    fun `create without logged in user returns 401`() {
        post("/hakemukset", AlluDataFactory.createApplication(id = null))
            .andExpect(status().isUnauthorized)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(userName)
    fun `create without body returns 400`() {
        post("/hakemukset").andExpect(status().isBadRequest)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(userName)
    fun `create with proper application creates application`() {
        val newApplication = AlluDataFactory.createApplication(id = null)
        val createdApplication = newApplication.copy(id = 1234)
        every { applicationService.create(newApplication, userName) } returns createdApplication

        val response: Application =
            post("/hakemukset", newApplication).andExpect(status().isOk).andReturnBody()

        assertEquals(createdApplication, response)
        verify { applicationService.create(newApplication, userName) }
    }

    @Test
    @WithMockUser(userName)
    fun `create with missing application data type returns 400`() {
        val application = AlluDataFactory.createApplication(id = null)
        val content: ObjectNode = OBJECT_MAPPER.valueToTree(application)
        (content.get("applicationData") as ObjectNode).remove("applicationType")

        postRaw("/hakemukset", content.toJsonString()).andExpect(status().isBadRequest)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(userName)
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
    @WithMockUser(userName)
    fun `update without body returns 400`() {
        put("/hakemukset/1234").andExpect(status().isBadRequest)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(userName)
    fun `update with known id returns ok`() {
        val application = AlluDataFactory.createApplication()
        every {
            applicationService.updateApplicationData(1234, application.applicationData, userName)
        } returns application

        val response: Application =
            put("/hakemukset/1234", application).andExpect(status().isOk).andReturnBody()

        assertEquals(application, response)
        verify {
            applicationService.updateApplicationData(1234, application.applicationData, userName)
        }
    }

    @Test
    @WithMockUser(userName)
    fun `update with missing application data type returns 400`() {
        val application = AlluDataFactory.createApplication()
        val content: ObjectNode = OBJECT_MAPPER.valueToTree(application)
        (content.get("applicationData") as ObjectNode).remove("applicationType")

        putRaw("/hakemukset/1234", content.toJsonString()).andExpect(status().isBadRequest)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(userName)
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
    @WithMockUser(userName)
    fun `update with unknown id returns 404`() {
        val application = AlluDataFactory.createApplication()
        every {
            applicationService.updateApplicationData(1234, application.applicationData, userName)
        } throws ApplicationNotFoundException(1234)

        put("/hakemukset/1234", application).andExpect(status().isNotFound)

        verify {
            applicationService.updateApplicationData(1234, application.applicationData, userName)
        }
    }

    @Test
    fun `sendApplication without logged in user returns 401`() {
        post("/hakemukset/1234/send-application", AlluDataFactory.createApplication())
            .andExpect(status().isUnauthorized)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(userName)
    fun `sendApplication without body returns 400`() {
        post("/hakemukset").andExpect(status().isBadRequest)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(userName)
    fun `sendApplication with proper application creates application`() {
        val newApplication = AlluDataFactory.createApplication(id = null)
        val createdApplication = newApplication.copy(id = 1234)
        every { applicationService.create(newApplication, userName) } returns createdApplication

        val response: Application =
            post("/hakemukset", newApplication).andExpect(status().isOk).andReturnBody()

        assertEquals(createdApplication, response)
        verify { applicationService.create(newApplication, userName) }
    }

    @Test
    @WithMockUser(userName)
    fun `sendApplication with missing application data type returns 400`() {
        val application = AlluDataFactory.createApplication(id = null)
        val content: ObjectNode = OBJECT_MAPPER.valueToTree(application)
        (content.get("applicationData") as ObjectNode).remove("applicationType")

        postRaw("/hakemukset", content.toJsonString()).andExpect(status().isBadRequest)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(userName)
    fun `sendApplication with missing application type returns 400`() {
        val application = AlluDataFactory.createApplication(id = null)
        val content: ObjectNode = OBJECT_MAPPER.valueToTree(application)
        content.remove("applicationType")

        postRaw("/hakemukset", content.toJsonString())
            .andDo { print(it) }
            .andExpect(status().isBadRequest)

        verify { applicationService wasNot Called }
    }

    @Test
    @WithMockUser(userName)
    fun `sendApplication with unknown id returns 404`() {
        val application = AlluDataFactory.createApplication()
        every {
            applicationService.updateApplicationData(1234, application.applicationData, userName)
        } throws ApplicationNotFoundException(1234)

        put("/hakemukset/1234", application).andExpect(status().isNotFound)

        verify {
            applicationService.updateApplicationData(1234, application.applicationData, userName)
        }
    }
}
