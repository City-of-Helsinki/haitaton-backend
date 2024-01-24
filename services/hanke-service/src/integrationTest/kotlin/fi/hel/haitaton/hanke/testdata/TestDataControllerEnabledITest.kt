package fi.hel.haitaton.hanke.testdata

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.andReturnBody
import fi.hel.haitaton.hanke.factory.ProfiiliFactory
import fi.hel.haitaton.hanke.profiili.Names
import fi.hel.haitaton.hanke.profiili.ProfiiliClient
import fi.hel.haitaton.hanke.profiili.VerifiedNameNotFound
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verify
import io.mockk.verifyAll
import java.net.SocketTimeoutException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
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
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class TestDataControllerEnabledITest(@Autowired override val mockMvc: MockMvc) : ControllerTest {

    @Autowired private lateinit var testDataService: TestDataService
    @Autowired private lateinit var profiiliClient: ProfiiliClient

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
    inner class VerifiedName {
        private val url = "$BASE_URL/verified-name"

        @Test
        @WithAnonymousUser
        fun `Without user ID returns 401`() {
            get(url).andExpect(MockMvcResultMatchers.status().isUnauthorized)

            verify { profiiliClient wasNot Called }
        }

        @Test
        fun `returns 404 when profiili client throws expected exception`() {
            every { profiiliClient.getVerifiedName(any()) } throws
                VerifiedNameNotFound("Because of reasons.")

            get(url).andExpect(MockMvcResultMatchers.status().isNotFound)

            verifyAll { profiiliClient.getVerifiedName(any()) }
        }

        @Test
        fun `returns 500 when Profiili client throws unexpected exception`() {
            every { profiiliClient.getVerifiedName(any()) } throws SocketTimeoutException()

            get(url).andExpect(MockMvcResultMatchers.status().isInternalServerError)

            verifyAll { profiiliClient.getVerifiedName(any()) }
        }

        @Test
        fun `returns verified names`() {
            every { profiiliClient.getVerifiedName(any()) } returns ProfiiliFactory.DEFAULT_NAMES

            val names: Names =
                get(url).andExpect(MockMvcResultMatchers.status().isOk).andReturnBody()

            assertThat(names).all {
                prop(Names::firstName).isEqualTo(ProfiiliFactory.DEFAULT_FIRST_NAME)
                prop(Names::lastName).isEqualTo(ProfiiliFactory.DEFAULT_LAST_NAME)
                prop(Names::givenName).isEqualTo(ProfiiliFactory.DEFAULT_GIVEN_NAME)
            }
            verifyAll { profiiliClient.getVerifiedName(any()) }
        }
    }
}
