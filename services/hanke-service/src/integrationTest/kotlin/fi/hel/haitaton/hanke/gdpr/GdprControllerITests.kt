package fi.hel.haitaton.hanke.gdpr

import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.application.ApplicationService
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.asJsonResource
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content

private const val USERID = "test-user"

@WebMvcTest(controllers = [GdprController::class], properties = ["haitaton.gdpr.disabled=false"])
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("itest")
@WithMockUser(USERID, roles = ["haitaton-user"])
class GdprControllerITests(@Autowired val mockMvc: MockMvc) {

    @Autowired lateinit var applicationService: ApplicationService

    @Autowired lateinit var disclosureLogService: DisclosureLogService

    @BeforeEach
    fun cleanup() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(applicationService, disclosureLogService)
    }

    @Test
    fun `When user has not created any applications, return 404`() {
        every { applicationService.getAllApplicationsCreatedByUser(USERID) }.returns(listOf())

        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/gdpr-api/$USERID").accept(MediaType.APPLICATION_JSON),
            )
            .andExpect(MockMvcResultMatchers.status().isNotFound)
            .andExpect(content().string(""))

        verify { applicationService.getAllApplicationsCreatedByUser(USERID) }
        verify { disclosureLogService wasNot Called }
    }

    @Test
    fun `When there's an internal error, return gdpr error response`() {
        every { applicationService.getAllApplicationsCreatedByUser(USERID) }
            .throws(RuntimeException())
        val expectedError =
            """{"errors": [{"code": "HAI0002", "message": {"fi": "Tuntematon virhe", "en": "Unknown error"}}]}"""

        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/gdpr-api/$USERID").accept(MediaType.APPLICATION_JSON),
            )
            .andExpect(MockMvcResultMatchers.status().`is`(500))
            .andExpect(content().json(expectedError))

        verify { applicationService.getAllApplicationsCreatedByUser(USERID) }
        verify { disclosureLogService wasNot Called }
    }

    @Test
    fun `When there's no matching names, return 404`() {
        every { applicationService.getAllApplicationsCreatedByUser(USERID) }.returns(listOf())

        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/gdpr-api/$USERID").accept(MediaType.APPLICATION_JSON),
            )
            .andExpect(MockMvcResultMatchers.status().`is`(404))
            .andExpect(content().string(""))

        verify { applicationService.getAllApplicationsCreatedByUser(USERID) }
    }

    @Test
    fun `When there are applications, return json response`() {
        CollectionNode(
            "user",
            listOf(
                StringNode("id", USERID),
                StringNode("nimi", "Teppo"),
                StringNode("puhelinnumero", "1234"),
            ),
        )
        val applicationData: CableReportApplicationData =
            "/fi/hel/haitaton/hanke/application/applicationData.json".asJsonResource()
        val application = AlluDataFactory.createApplication(applicationData = applicationData)
        every { applicationService.getAllApplicationsCreatedByUser(USERID) }
            .returns(listOf(application))
        val expectedResponse =
            """{
                "key":"user",
                "children": [
                  {
                    "key":"id",
                    "value":"test-user"
                  },
                  {
                    "key":"nimi",
                    "value":"Teppo Testihenkilö"
                  },
                  {
                    "key":"puhelinnumero",
                    "value":"04012345678"
                  },
                  {
                    "key":"sahkopostit",
                    "children": [
                      {
                        "key":"sahkoposti",
                        "value":"teppo@example.test"
                      },
                      {
                        "key":"sahkoposti",
                        "value":"teppo@dna.test"
                      }
                    ]
                  },
                  {
                    "key":"organisaatio",
                    "children":[
                      {
                        "key":"nimi",
                        "value":"Dna"
                      },
                      {
                        "key":"tunnus",
                        "value":"3766028-0"
                      }
                    ]
                  }
                ]
              }
            """.trimIndent()

        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/gdpr-api/$USERID").accept(MediaType.APPLICATION_JSON),
            )
            .andExpect(MockMvcResultMatchers.status().`is`(200))
            .andExpect(content().json(expectedResponse))

        verify { applicationService.getAllApplicationsCreatedByUser(USERID) }
        verify { disclosureLogService.saveDisclosureLogsForProfiili(USERID, any<CollectionNode>()) }
    }
}
