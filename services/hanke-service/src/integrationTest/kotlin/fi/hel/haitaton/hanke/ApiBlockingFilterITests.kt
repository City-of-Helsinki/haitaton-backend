package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.factory.ProfiiliFactory.DEFAULT_NAMES
import fi.hel.haitaton.hanke.hakemus.HakemusController
import fi.hel.haitaton.hanke.permissions.HankeKayttajaController
import fi.hel.haitaton.hanke.profiili.ProfiiliClient
import fi.hel.haitaton.hanke.profiili.ProfiiliController
import fi.hel.haitaton.hanke.test.USERNAME
import io.mockk.every
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.security.test.context.support.WithAnonymousUser
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
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
    properties = ["haitaton.api.disabled=true", "management.server.port="],
)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
class ApiBlockingFilterITests(@Autowired override val mockMvc: MockMvc) : ControllerTest {

    @Nested
    inner class BlockedEndpoints {

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
                ],
            delimiter = ' '
        )
        @WithMockUser(username = USERNAME)
        fun `should return 503 'Service Unavailable' for blocked endpoints when the user is authenticated`(
            method: String,
            path: String
        ) {
            when (method) {
                "GET" -> getRaw(path).andExpect(status().isServiceUnavailable)
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
                ],
            delimiter = ' '
        )
        @WithAnonymousUser
        fun `should return 401 'Unauthorized' for blocked endpoints when the user is not authenticated`(
            method: String,
            path: String
        ) {
            when (method) {
                "GET" -> getRaw(path).andExpect(status().isUnauthorized)
                "POST" -> post(path).andExpect(status().isUnauthorized)
                "PUT" -> put(path).andExpect(status().isUnauthorized)
                "DELETE" -> delete(path).andExpect(status().isUnauthorized)
            }
        }
    }

    @Nested
    inner class AllowedEndpoints {
        @Autowired lateinit var profiiliClient: ProfiiliClient
        @Autowired lateinit var jdbcOperations: JdbcOperations

        @Nested
        inner class ProfiiliEndpoint {

            @Test
            @WithMockUser(username = USERNAME)
            fun `should return normal data for profiili endpoint when the user is authenticated`() {
                every { profiiliClient.getVerifiedName(any()) } returns DEFAULT_NAMES
                get("/profiili/verified-name")
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.firstName").value(DEFAULT_NAMES.firstName))
            }

            @Test
            @WithAnonymousUser
            fun `should return 401 'Unauthorized' for profiili endpoint when the user is not authenticated`() {
                getRaw("/profiili/verified-name").andExpect(status().isUnauthorized)
            }
        }

        @Nested
        @WithAnonymousUser
        inner class StatusEndpoint {

            @Test
            fun `should return 200 'OK' for status endpoint`() {
                every {
                    jdbcOperations.queryForObject(StatusController.QUERY, Boolean::class.java)
                } returns true
                getRaw("/status").andExpect(status().isOk)
            }
        }
    }
}
