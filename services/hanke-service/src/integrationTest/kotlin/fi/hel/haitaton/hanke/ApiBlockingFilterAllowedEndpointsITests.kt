package fi.hel.haitaton.hanke

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    properties = ["haitaton.api.disabled=true", "management.server.port="],
)
@AutoConfigureMockMvc
class ApiBlockingFilterAllowedEndpointsITests(@Autowired override val mockMvc: MockMvc) :
    ControllerTest, IntegrationTest() {

    @Test
    fun `should return 200 'OK' for actuator liveness path`() {
        get("/actuator/health/liveness").andExpect(status().isOk)
    }

    @Test
    fun `should return 200 'OK' for actuator readiness path`() {
        get("/actuator/health/readiness").andExpect(status().isOk)
    }

    @Test
    fun `should return 200 'OK' for status path`() {
        get("/status", null).andExpect(status().isOk)
    }
}
