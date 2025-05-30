package fi.hel.haitaton.hanke.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

private val log = mu.KotlinLogging.logger {}

/**
 * Filter that checks if the user session is already known. If not, it saves the session to the
 * database.
 */
@Component
class UserSessionFilter(
    private val userSessionService: UserSessionService,
    private val cache: UserSessionCache,
) : OncePerRequestFilter() {
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
            val key = "$sub|$sid"

            if (!cache.isSessionKnown(key)) {
                cache.markSessionAsSeen(key)
                userSessionService.saveSessionIfNotExists(sub, sid, jwt.issuedAt)
                log.info { "New user session saved" }
            }
        }

        filterChain.doFilter(request, response)
    }
}
