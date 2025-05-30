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

    private val userSessionService = mockk<UserSessionService>(relaxed = true)
    private val sessionCache = mockk<UserSessionCache>(relaxed = true)
    private val filter = UserSessionFilter(userSessionService, sessionCache)

    private val chain = mockk<FilterChain>(relaxed = true)
    private val request: HttpServletRequest = mockk()
    private val response: HttpServletResponse = mockk()

    companion object {
        private const val SUB = "user-abc"
        private const val SID = "session-123"
        private val ISSUED_AT = Instant.now()
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
            }
        val auth = JwtAuthenticationToken(jwt)
        SecurityContextHolder.getContext().authentication = auth
    }

    @AfterEach
    fun tearDown() {
        confirmVerified(userSessionService, sessionCache, chain)
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `saves new user session`() {
        every { sessionCache.isSessionKnown("$SUB|$SID") } returns false

        filter.doFilterInternal(request, response, chain)

        verifySequence {
            sessionCache.isSessionKnown("$SUB|$SID")
            sessionCache.markSessionAsSeen("$SUB|$SID")
            userSessionService.saveSessionIfNotExists(SUB, SID, ISSUED_AT)
            chain.doFilter(request, response)
        }
    }

    @Test
    fun `does not save cached user session`() {
        every { sessionCache.isSessionKnown("$SUB|$SID") } returns true

        filter.doFilterInternal(request, response, chain)

        verifySequence {
            sessionCache.isSessionKnown("$SUB|$SID")
            chain.doFilter(request, response)
        }
        verify(exactly = 0) { sessionCache.markSessionAsSeen(any()) }
        verify(exactly = 0) { userSessionService.saveSessionIfNotExists(any(), any(), any()) }
    }
}
