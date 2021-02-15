package fi.hel.haitaton.hanke.organisaatio

import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import io.mockk.every
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("itest")
@WithMockUser("test", roles = ["haitaton-user"])
class OrganisaatioControllerEndpointITests(@Autowired val mockMvc: MockMvc) {

    @Autowired
    lateinit var organisaatioService: OrganisaatioService

    @Test
    fun `Get list of organisaatiot (GET)`() {
        val organisations = listOf(
                Organisaatio(1, "DNA", "DNA Oy"),
                Organisaatio(2, "WELHO", "DNA WELHO Oy")
        )

        every { organisaatioService.getOrganisaatiot() }.returns(organisations)

        mockMvc
                .perform(get("/organisaatiot/"))
                .andExpect(status().isOk)
                .andExpect(content().contentType("application/json"))
                .andExpect(
                        jsonPath("$[*].id").value(
                                containsInAnyOrder
                                (1, 2)
                        )
                )
                .andExpect(
                        jsonPath("$[*].nimi").value(
                                containsInAnyOrder
                                ("DNA Oy", "DNA WELHO Oy")
                        )
                )
    }
}
