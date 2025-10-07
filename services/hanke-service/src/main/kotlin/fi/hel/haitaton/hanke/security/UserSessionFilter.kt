package fi.hel.haitaton.hanke.security

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.filter.OncePerRequestFilter

private val log = mu.KotlinLogging.logger {}

/**
 * Filter that validates user sessions and saves new sessions to the database.
 *
 * For authenticated requests with a session ID (sid), this filter:
 * 1. Delegates to UserSessionService to validate and save the session
 * 2. If session validation fails, rejects request with SESSION_TERMINATED error
 */
class UserSessionFilter(private val userSessionService: UserSessionService) :
    OncePerRequestFilter() {
    public override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val authentication = SecurityContextHolder.getContext().authentication

        if (authentication is JwtAuthenticationToken) {
            val jwt = authentication.token
            val sub = jwt.subject
            val sid = jwt.getClaim<String>("sid")

            if (sid != null) {
                val isValid =
                    userSessionService.validateAndSaveSession(sub, sid, jwt.issuedAt, jwt.expiresAt)

                if (!isValid) {
                    log.warn { "Session terminated for user, rejecting request. sessionId=$sid" }
                    response.status = HttpServletResponse.SC_UNAUTHORIZED
                    response.contentType = MediaType.APPLICATION_JSON_VALUE
                    response.writer.print(OBJECT_MAPPER.writeValueAsString(HankeError.HAI0006))
                    return // Don't continue the filter chain
                }
            }
        }

        filterChain.doFilter(request, response)
    }
}
