package fi.hel.haitaton.hanke.profiili

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.andReturnBody
import fi.hel.haitaton.hanke.factory.ProfiiliFactory
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.security.JwtClaims
import fi.hel.haitaton.hanke.test.USERNAME
import io.mockk.Called
import io.mockk.Runs
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
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

private const val BASE_URL = "/profiili"

@WebMvcTest(controllers = [ProfiiliController::class])
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class ProfiiliControllerITest(@Autowired override val mockMvc: MockMvc) : ControllerTest {

    @Autowired private lateinit var profiiliService: ProfiiliService
    @Autowired private lateinit var disclosureLogService: DisclosureLogService

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
    }

    @Nested
    inner class VerifiedName {
        private val url = "$BASE_URL/verified-name"

        @Test
        @WithAnonymousUser
        fun `Without user ID returns 401`() {
            get(url).andExpect(MockMvcResultMatchers.status().isUnauthorized)

            verify { profiiliService wasNot Called }
            verify { disclosureLogService wasNot Called }
        }

        @Test
        fun `returns 404 when profiili client throws expected exception`() {
            every { profiiliService.getVerifiedName(any()) } throws
                VerifiedNameNotFound("Because of reasons.")

            get(url).andExpect(MockMvcResultMatchers.status().isNotFound)

            verifyAll { profiiliService.getVerifiedName(any()) }
            verify { disclosureLogService wasNot Called }
        }

        @Test
        fun `returns 404 when name is not found from AD-token`() {
            every { profiiliService.getVerifiedName(any()) } throws
                NameClaimNotFound(JwtClaims.FAMILY_NAME)

            get(url).andExpect(MockMvcResultMatchers.status().isNotFound)

            verifyAll { profiiliService.getVerifiedName(any()) }
            verify { disclosureLogService wasNot Called }
        }

        @Test
        fun `returns 500 when Profiili client throws unexpected exception`() {
            every { profiiliService.getVerifiedName(any()) } throws SocketTimeoutException()

            get(url).andExpect(MockMvcResultMatchers.status().isInternalServerError)

            verifyAll { profiiliService.getVerifiedName(any()) }
            verify { disclosureLogService wasNot Called }
        }

        @Test
        fun `returns verified names`() {
            every { profiiliService.getVerifiedName(any()) } returns ProfiiliFactory.DEFAULT_NAMES
            every { disclosureLogService.saveForProfiiliNimi(any(), any()) } just Runs

            val names: Names =
                get(url).andExpect(MockMvcResultMatchers.status().isOk).andReturnBody()

            assertThat(names).all {
                prop(Names::firstName).isEqualTo(ProfiiliFactory.DEFAULT_FIRST_NAME)
                prop(Names::lastName).isEqualTo(ProfiiliFactory.DEFAULT_LAST_NAME)
                prop(Names::givenName).isEqualTo(ProfiiliFactory.DEFAULT_GIVEN_NAME)
            }
            verifyAll {
                profiiliService.getVerifiedName(any())
                disclosureLogService.saveForProfiiliNimi(any(), any())
            }
        }
    }
}
