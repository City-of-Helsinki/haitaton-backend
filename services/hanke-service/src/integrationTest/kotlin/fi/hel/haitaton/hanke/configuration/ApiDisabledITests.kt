package fi.hel.haitaton.hanke.configuration

import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HankeController
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.hakemus.HakemusController
import fi.hel.haitaton.hanke.test.USERNAME
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@WebMvcTest(
    value = [ApiDisabledConfiguration::class, HankeController::class, HakemusController::class],
    properties = ["haitaton.api.enabled=false"]
)
@Import(IntegrationTestConfiguration::class)
@WithMockUser(USERNAME)
class ApiDisabledITests(@Autowired override val mockMvc: MockMvc) : ControllerTest {

    @ParameterizedTest
    @CsvSource(
        value =
            [
                "GET /public-hankkeet",
                "GET /my-permissions",
                "POST /hankkeet/",
                "PUT /hankkeet/HAI24-1",
                "POST /hakemukset",
                "PUT /hakemukset/123456",
                "POST /johtoselvityshakemus",
                "DELETE /kayttajat/000"
            ],
        delimiter = ' '
    )
    fun `should return Service Unavailable 503`(method: String, path: String) {
        when (method) {
            "GET" -> get(path)
            "POST" -> post(path)
            "PUT" -> put(path)
            "DELETE" -> delete(path)
            else -> throw IllegalArgumentException("Unsupported method: $method")
        }.andExpect(MockMvcResultMatchers.status().isServiceUnavailable)
    }
}
