package fi.hel.haitaton.hanke.security

import assertk.assertThat
import assertk.assertions.isEqualTo
import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.andReturnBody
import fi.hel.haitaton.hanke.test.USERNAME
import io.mockk.Runs
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.just
import io.mockk.verify
import io.mockk.verifySequence
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private const val PATH = "/backchannel-logout"
private const val TOKEN_NAME = "logout_token"
private const val TOKEN_VALUE = "logout-token"

@WebMvcTest(controllers = [BackChannelLogoutController::class])
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class BackChannelLogoutControllerITest(@Autowired override val mockMvc: MockMvc) : ControllerTest {

    @Autowired lateinit var logoutService: LogoutService

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(logoutService)
    }

    @Test
    fun `returns 200 when logout succeeds`() {
        every { logoutService.logout(TOKEN_VALUE) } just Runs

        postForm(PATH, "$TOKEN_NAME=$TOKEN_VALUE").andExpect(status().isOk)

        verifySequence { logoutService.logout(TOKEN_VALUE) }
    }

    @Test
    fun `returns 400 if token is missing`() {
        val (error, _) =
            postForm(PATH, null)
                .andExpect(status().isBadRequest)
                .andReturnBody<BackChannelLogoutError>()

        assertThat(error).isEqualTo("InvalidRequest")

        verify(exactly = 0) { logoutService.logout(any()) }
    }

    @Test
    fun `returns 400 if token is invalid`() {
        every { logoutService.logout("invalid-token") } throws JwtException("Invalid token")

        val (error, _) =
            postForm(PATH, "$TOKEN_NAME=invalid-token")
                .andExpect(status().isBadRequest)
                .andReturnBody<BackChannelLogoutError>()

        assertThat(error).isEqualTo("InvalidToken")

        verifySequence { logoutService.logout("invalid-token") }
    }

    @Test
    fun `returns 500 if handling fails`() {
        every { logoutService.logout(TOKEN_VALUE) } throws RuntimeException()

        val (error, _) =
            postForm(PATH, "$TOKEN_NAME=$TOKEN_VALUE")
                .andExpect(status().isInternalServerError)
                .andReturnBody<BackChannelLogoutError>()

        assertThat(error).isEqualTo("InternalServerError")

        verifySequence { logoutService.logout(TOKEN_VALUE) }
    }
}
