package fi.hel.haitaton.hanke.hello

import com.ninjasquad.springmockk.MockkBean
import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import org.hamcrest.Matchers.stringContainsInOrder
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

/**
 * Testing the Hello API through a full REST request.
 *
 * This class should test only the weblayer (both HTTP server and context to be auto-mocked).
 */
@WebMvcTest
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("itest")
class HelloApiControllerITests(@Autowired val mockMvc: MockMvc) {

    @Test
    fun `Hello response at web layer`() {
        // First call
        mockMvc.perform(get("/api/hello/").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.count").value("1"))
                .andExpect(jsonPath("$.message").value(stringContainsInOrder(listOf("Hello", "world"))))

        // Second call
        mockMvc.perform(get("/api/hello/").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.count").value("2"))
                .andExpect(jsonPath("$.message").value(stringContainsInOrder(listOf("again", "world"))))
    }

}
