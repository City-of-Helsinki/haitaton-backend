package fi.hel.haitaton.hanke.organisaatio

import com.ninjasquad.springmockk.MockkBean
import fi.hel.haitaton.hanke.HankeService
import io.mockk.every
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*


/**
 * For testing Spring Boot Actuator endpoints
 */
@SpringBootTest(properties = [
    "management.server.port=",
    "spring.liquibase.enabled=false",
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"])
@AutoConfigureMockMvc
@EnableAutoConfiguration
class OrgansaatioControllerEndpointITests(@Autowired val mockMvc: MockMvc) {

    // Just to prevent the context trying to init that service, and fail doing it.
    @MockBean
    lateinit var hankeService: HankeService

    @MockkBean
    lateinit var organisaatioService: OrganisaatioService

    @Test
    fun `Get list of organisaatiot (GET)`() {
        val organisations = listOf( Organisaatio(1, "DNA", "DNA Oy"),
                Organisaatio(2, "WELHO", "DNA WELHO Oy"))

        every { organisaatioService.getOrganisaatiot() }.returns(organisations)

        mockMvc
                .perform(get("/organisaatiot/"))
                .andExpect(status().isOk)
                .andExpect(content().contentType("application/json"))
                .andExpect(jsonPath("$[*].id").value(containsInAnyOrder
                (1, 2)))
                .andExpect(jsonPath("$[*].nimi").value(containsInAnyOrder
                ("DNA Oy", "DNA WELHO Oy")))
    }

}
