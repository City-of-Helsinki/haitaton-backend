package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.hakemus.HakemusController
import fi.hel.haitaton.hanke.permissions.HankeKayttajaController
import fi.hel.haitaton.hanke.profiili.ProfiiliController
import fi.hel.haitaton.hanke.test.USERNAME
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithAnonymousUser
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(
    controllers =
        [
            HakemusController::class,
            HankeController::class,
            HankeKayttajaController::class,
            PublicHankeController::class,
            StatusController::class,
            ProfiiliController::class,
        ],
    properties = ["haitaton.api.disabled=true"],
)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
class ApiBlockingFilterBlockedEndpointsITests(@Autowired override val mockMvc: MockMvc) :
    ControllerTest {

    @ParameterizedTest
    @CsvSource(
        value =
            [
                "GET /my-permissions",
                "POST /hankkeet",
                "PUT /hankkeet/HAI24-1",
                "POST /hakemukset",
                "PUT /hakemukset/123456",
                "POST /johtoselvityshakemus",
                "DELETE /kayttajat/000",
                "GET /public-hankkeet",
                "GET /profiili/verified-name",
                "GET /swagger-ui/index.html",
            ],
        delimiter = ' '
    )
    @WithMockUser(username = USERNAME)
    fun `should return 503 'Service Unavailable' for blocked endpoints when the user is authenticated`(
        method: String,
        path: String
    ) {
        when (method) {
            "GET" -> get(path, null).andExpect(status().isServiceUnavailable)
            "POST" -> post(path).andExpect(status().isServiceUnavailable)
            "PUT" -> put(path).andExpect(status().isServiceUnavailable)
            "DELETE" -> delete(path).andExpect(status().isServiceUnavailable)
        }
    }

    @ParameterizedTest
    @CsvSource(
        value =
            [
                "GET /my-permissions",
                "POST /hankkeet",
                "PUT /hankkeet/HAI24-1",
                "POST /hakemukset",
                "PUT /hakemukset/123456",
                "POST /johtoselvityshakemus",
                "DELETE /kayttajat/000",
                "GET /public-hankkeet",
                "GET /profiili/verified-name",
                "GET /swagger-ui/index.html",
            ],
        delimiter = ' '
    )
    @WithAnonymousUser
    fun `should return 503 'Service Unavailable' for blocked endpoints when the user is not authenticated`(
        method: String,
        path: String
    ) {
        when (method) {
            "GET" -> get(path, null).andExpect(status().isServiceUnavailable)
            "POST" -> post(path).andExpect(status().isServiceUnavailable)
            "PUT" -> put(path).andExpect(status().isServiceUnavailable)
            "DELETE" -> delete(path).andExpect(status().isServiceUnavailable)
        }
    }
}
