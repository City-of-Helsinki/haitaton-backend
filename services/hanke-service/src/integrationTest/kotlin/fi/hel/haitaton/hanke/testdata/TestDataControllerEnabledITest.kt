package fi.hel.haitaton.hanke.testdata

import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.verifyAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithAnonymousUser
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

private const val USERNAME = "testUser"
private const val BASE_URL = "/testdata"

@WebMvcTest(
    controllers = [TestDataController::class],
    properties = ["haitaton.testdata.enabled=true"],
)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("itest")
@WithMockUser(USERNAME)
class TestDataControllerEnabledITest(@Autowired override val mockMvc: MockMvc) : ControllerTest {

    @Autowired private lateinit var testDataService: TestDataService
    @Value("\${haitaton.testdata.enabled}") private lateinit var enabled: String

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
}
