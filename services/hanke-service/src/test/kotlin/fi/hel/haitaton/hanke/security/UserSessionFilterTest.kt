package fi.hel.haitaton.hanke.security

import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import java.time.Instant
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

class UserSessionFilterTest {

    private val userSessionService = mockk<UserSessionService>()
    private val filter = UserSessionFilter(userSessionService)

    private val chain = mockk<FilterChain>(relaxed = true)
    private val request: HttpServletRequest = mockk()
    private val response: HttpServletResponse = mockk(relaxed = true)

    companion object {
        private const val SUB = "user-abc"
        private const val SID = "session-123"
        private val ISSUED_AT = Instant.now()
        private val EXPIRES_AT = Instant.now().plusSeconds(3600)
    }

    @BeforeEach
    fun setup() {
        clearAllMocks()
        SecurityContextHolder.clearContext()
        val jwt =
            mockk<Jwt> {
                every { subject } returns SUB
                every { getClaim<String>("sid") } returns SID
                every { issuedAt } returns ISSUED_AT
                every { expiresAt } returns EXPIRES_AT
            }
        val auth = JwtAuthenticationToken(jwt)
        SecurityContextHolder.getContext().authentication = auth
    }

    @AfterEach
    fun tearDown() {
        confirmVerified(userSessionService, chain)
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `proceeds normally when session validation succeeds`() {
        every { userSessionService.validateAndSaveSession(SUB, SID, ISSUED_AT, EXPIRES_AT) } returns
            true

        filter.doFilterInternal(request, response, chain)

        verifySequence {
            userSessionService.validateAndSaveSession(SUB, SID, ISSUED_AT, EXPIRES_AT)
            chain.doFilter(request, response)
        }
    }

    @Test
    fun `rejects request when session validation fails`() {
        every { userSessionService.validateAndSaveSession(SUB, SID, ISSUED_AT, EXPIRES_AT) } returns
            false

        filter.doFilterInternal(request, response, chain)

        verifySequence {
            userSessionService.validateAndSaveSession(SUB, SID, ISSUED_AT, EXPIRES_AT)
        }
        verify(exactly = 0) { chain.doFilter(any(), any()) }
    }
}
