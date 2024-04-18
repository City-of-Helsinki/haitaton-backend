package fi.hel.haitaton.hanke

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithAnonymousUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    properties = ["haitaton.api.disabled=true", "management.server.port="],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithAnonymousUser
class ApiBlockingFilterActuatorITests(@Autowired override val mockMvc: MockMvc) : ControllerTest {

    @ParameterizedTest
    @ValueSource(strings = ["/actuator/health/liveness", "/actuator/health/readiness"])
    fun `should return 200 'OK' for actuator paths`(path: String) {
        getRaw(path).andExpect(status().isOk)
    }
}
