package fi.hel.haitaton.hanke.gdpr

import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.allu.ApplicationService
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.factory.UserInfoFactory
import fi.hel.haitaton.hanke.profiili.ProfiiliClient
import io.mockk.called
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
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

@WebMvcTest(controllers = [GdprController::class], properties = ["haitaton.gdpr.disabled=false"])
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("itest")
@WithMockUser("test", roles = ["haitaton-user"])
class GdprControllerITests(@Autowired val mockMvc: MockMvc) {

    @Autowired lateinit var applicationService: ApplicationService
    @Autowired lateinit var hankeService: HankeService
    @Autowired lateinit var profiiliClient: ProfiiliClient
    @Autowired lateinit var gdprJsonConverter: GdprJsonConverter

    @AfterEach
    fun cleanup() {
        clearAllMocks()
    }

    @Test
    fun `When user has no information, return 404`() {
        every { applicationService.getAllApplicationsForUser("test") }.returns(listOf())
        every { hankeService.loadHankkeetByUserId("test") }.returns(listOf())

        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/gdpr-api/test").accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(MockMvcResultMatchers.status().isNotFound)
            .andExpect(content().string(""))

        verify { applicationService.getAllApplicationsForUser("test") }
        verify { hankeService.loadHankkeetByUserId("test") }
        verify { profiiliClient wasNot called }
        verify { gdprJsonConverter wasNot called }
    }

    @Test
    fun `When there's an internal error, return gdpr error response`() {
        every { applicationService.getAllApplicationsForUser("test") }.throws(RuntimeException())

        val expectedError =
            """{"errors": [{"code": "HAI0002", "message": {"fi": "Tuntematon virhe", "en": "Unknown error"}}]}"""
        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/gdpr-api/test").accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(MockMvcResultMatchers.status().`is`(500))
            .andExpect(content().json(expectedError))

        verify { applicationService.getAllApplicationsForUser("test") }
        verify { hankeService wasNot called }
        verify { profiiliClient wasNot called }
        verify { gdprJsonConverter wasNot called }
    }

    @Test
    fun `When there's no matching names, return 404`() {
        every { applicationService.getAllApplicationsForUser("test") }.returns(listOf())
        every { hankeService.loadHankkeetByUserId("test") }.returns(listOf(Hanke()))
        every { profiiliClient.getInfo("test") }.returns(UserInfoFactory.teppoUserInfo())
        every { gdprJsonConverter.createGdprJson(any(), listOf(Hanke()), any()) }.returns(null)

        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/gdpr-api/test").accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(MockMvcResultMatchers.status().`is`(404))
            .andExpect(content().string(""))

        verify { applicationService.getAllApplicationsForUser("test") }
        verify { hankeService.loadHankkeetByUserId("test") }
        verify { profiiliClient.getInfo("test") }
        verify { gdprJsonConverter.createGdprJson(any(), listOf(Hanke()), any()) }
    }

    @Test
    fun `When there are matching names, return json response`() {
        every { applicationService.getAllApplicationsForUser("test") }.returns(listOf())
        every { hankeService.loadHankkeetByUserId("test") }.returns(listOf(Hanke()))
        every { profiiliClient.getInfo("test") }.returns(UserInfoFactory.teppoUserInfo())
        every { gdprJsonConverter.createGdprJson(any(), listOf(Hanke()), any()) }
            .returns(
                CollectionNode(
                    "user",
                    listOf(StringNode("nimi", "Teppo"), StringNode("puhelinnumero", "1234"))
                )
            )
        val expectedResponse =
            """{"key": "user", "children": [{"key": "nimi", "value": "Teppo"},{"key": "puhelinnumero", "value": "1234"}]}"""

        mockMvc
            .perform(
                MockMvcRequestBuilders.get("/gdpr-api/test").accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(MockMvcResultMatchers.status().`is`(200))
            .andExpect(content().json(expectedResponse))

        verify { applicationService.getAllApplicationsForUser("test") }
        verify { hankeService.loadHankkeetByUserId("test") }
        verify { profiiliClient.getInfo("test") }
        verify { gdprJsonConverter.createGdprJson(any(), listOf(Hanke()), any()) }
    }
}
