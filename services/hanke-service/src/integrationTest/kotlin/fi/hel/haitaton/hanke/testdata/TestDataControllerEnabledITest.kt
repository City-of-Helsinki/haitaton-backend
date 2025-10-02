package fi.hel.haitaton.hanke.testdata

import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.test.USERNAME
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verify
import io.mockk.verifyAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithAnonymousUser
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

private const val BASE_URL = "/testdata"

@WebMvcTest(
    controllers = [TestDataController::class],
    properties = ["haitaton.testdata.enabled=true"],
)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class TestDataControllerEnabledITest(@Autowired override val mockMvc: MockMvc) : ControllerTest {

    @Autowired private lateinit var testDataService: TestDataService

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(testDataService)
    }

    @Nested
    inner class UnlinkApplicationsFromAllu {
        private val url = "$BASE_URL/unlink-applications"

        @Test
        @WithAnonymousUser
        fun `Without user ID calls service normally`() {
            post(url).andExpect(MockMvcResultMatchers.status().isOk)

            verifyAll { testDataService.unlinkApplicationsFromAllu() }
        }

        @Test
        fun `With valid user calls service`() {
            post(url).andExpect(MockMvcResultMatchers.status().isOk)

            verifyAll { testDataService.unlinkApplicationsFromAllu() }
        }
    }

    @Nested
    inner class TriggerAlluUpdates {
        private val url = "$BASE_URL/trigger-allu"

        @Test
        @WithAnonymousUser
        fun `Without user ID calls service normally`() {
            get(url, MediaType.TEXT_PLAIN).andExpect(MockMvcResultMatchers.status().isOk)

            verifyAll { testDataService.triggerAlluUpdates() }
        }

        @Test
        fun `With valid user calls service`() {
            get(url, MediaType.TEXT_PLAIN).andExpect(MockMvcResultMatchers.status().isOk)

            verifyAll { testDataService.triggerAlluUpdates() }
        }
    }

    @Nested
    inner class CreatePublicHanke {
        private val url = "$BASE_URL/create-public-hanke/%d"

        @Test
        @WithAnonymousUser
        fun `Without user ID calls service normally`() {
            every { testDataService.createRandomPublicHanke(10) } returns 10

            post(String.format(url, 10), MediaType.TEXT_PLAIN)
                .andExpect(MockMvcResultMatchers.status().isOk)

            verifyAll { testDataService.createRandomPublicHanke(10) }
        }

        @Test
        fun `With valid user calls service`() {
            every { testDataService.createRandomPublicHanke(10) } returns 10

            post(String.format(url, 10), MediaType.TEXT_PLAIN)
                .andExpect(MockMvcResultMatchers.status().isOk)

            verifyAll { testDataService.createRandomPublicHanke(10) }
        }

        @Test
        fun `Returns 400 when count is less than 1`() {
            post(String.format(url, 0), MediaType.TEXT_PLAIN)
                .andExpect(MockMvcResultMatchers.status().isBadRequest)

            verify { testDataService wasNot Called }
        }

        @Test
        fun `Returns 400 when count is over 1000`() {
            post(String.format(url, 1001), MediaType.TEXT_PLAIN)
                .andExpect(MockMvcResultMatchers.status().isBadRequest)

            verify { testDataService wasNot Called }
        }
    }

    @Nested
    inner class TerminateCurrentSession {
        private val url = "$BASE_URL/terminate-session"

        @Test
        @WithAnonymousUser
        fun `returns 401 when not authenticated`() {
            delete(url).andExpect(MockMvcResultMatchers.status().isUnauthorized)

            verify { testDataService wasNot Called }
        }
    }
}
