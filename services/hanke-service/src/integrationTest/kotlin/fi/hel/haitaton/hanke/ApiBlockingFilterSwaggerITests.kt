package fi.hel.haitaton.hanke

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithAnonymousUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    properties = ["haitaton.api.disabled=true"],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithAnonymousUser
class ApiBlockingFilterSwaggerITests(@Autowired override val mockMvc: MockMvc) : ControllerTest {

    @Test
    fun `should return 200 'OK' for Swagger UI page`() {
        getRaw("/swagger-ui/index.html").andExpect(status().isOk)
    }
}
