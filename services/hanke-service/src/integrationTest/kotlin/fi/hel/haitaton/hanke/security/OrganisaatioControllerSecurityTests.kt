package fi.hel.haitaton.hanke.security

import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.IntegrationTestResourceServerConfig
import fi.hel.haitaton.hanke.organisaatio.Organisaatio
import fi.hel.haitaton.hanke.organisaatio.OrganisaatioController
import fi.hel.haitaton.hanke.organisaatio.OrganisaatioService
import io.mockk.every
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Tests to ensure OrganisaatioController has correct authentication and
 * authorization restrictions and handling. (Currently not restricted at all.)
 */
@WebMvcTest(OrganisaatioController::class)
@Import(IntegrationTestConfiguration::class, IntegrationTestResourceServerConfig::class)
@ActiveProfiles("itest")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrganisaatioControllerSecurityTests(@Autowired val mockMvc: MockMvc) {

    @Autowired
    lateinit var organisaatioService: OrganisaatioService

    @Test
    @WithMockUser(username = "test7358", roles = ["haitaton-user"])
    fun `status ok with authenticated user with correct role`() {
        performGetOrganisaatiot().andExpect(status().isOk)
    }

    @Test
    // Without mock user, i.e. anonymous
    fun `status ok without authenticated user`() {
        performGetOrganisaatiot().andExpect(status().isOk)
    }

    @Test
    @WithMockUser(username = "test7358", roles = ["bad role"])
    fun `status ok with authenticated user with bad role`() {
        performGetOrganisaatiot().andExpect(status().isOk)
    }

    @Test
    @WithMockUser(username = "test7358", roles = [])
    fun `status ok with authenticated user without roles`() {
        performGetOrganisaatiot().andExpect(status().isOk)
    }

    private fun performGetOrganisaatiot(): ResultActions {
        val organisations = listOf(
                Organisaatio(1, "DNA", "DNA Oy"),
                Organisaatio(2, "WELHO", "DNA WELHO Oy")
        )

        every { organisaatioService.getAll() }.returns(organisations)

        return mockMvc.perform(get("/organisaatiot/"))
    }
}
