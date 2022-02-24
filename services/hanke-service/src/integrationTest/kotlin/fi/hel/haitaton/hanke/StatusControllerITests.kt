package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.StatusController.Companion.QUERY
import fi.hel.haitaton.hanke.StatusController.Companion.SUCCESS
import fi.hel.haitaton.hanke.StatusController.Companion.ERROR
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@WebMvcTest(StatusController::class)
@Import(IntegrationTestConfiguration::class, IntegrationTestResourceServerConfig::class)
@ActiveProfiles("itest")
class StatusControllerITests(@Autowired val mockMvc: MockMvc) {

    @Autowired
    lateinit var jdbcOperations: JdbcOperations

    @Test
    fun testSuccess() {
        every { jdbcOperations.queryForObject(QUERY, Int::class.java) } returns SUCCESS
        mockMvc.perform(MockMvcRequestBuilders.get("/status"))
                .andExpect(MockMvcResultMatchers.status().isOk)
    }

    @Test
    fun testError() {
        every { jdbcOperations.queryForObject(QUERY, Int::class.java) } returns ERROR
        mockMvc.perform(MockMvcRequestBuilders.get("/status"))
                .andExpect(MockMvcResultMatchers.status().is5xxServerError)
    }

    @Test
    fun testException() {
        every { jdbcOperations.queryForObject(QUERY, Int::class.java) } throws RuntimeException()
        mockMvc.perform(MockMvcRequestBuilders.get("/status"))
                .andExpect(MockMvcResultMatchers.status().is5xxServerError)
    }

}
