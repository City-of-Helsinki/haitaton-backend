package fi.hel.haitaton.hanke

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

private val log =
    KotlinLogging
        .logger {} // named 'log' since 'logger' is already in super-class (and is not compatible)

/**
 * In order to do maintenance tasks, we need to be able to block calls to the API and return 503
 * Service Unavailable.
 */
@Order(
    -101
) // comes before Spring Security Filter (see spring.security.filter.order in application.yml)
@Component
@ConditionalOnProperty(name = ["haitaton.api.disabled"], havingValue = "true")
class ApiBlockingFilter : OncePerRequestFilter() {

    override fun initFilterBean() {
        super.initFilterBean()
        log.info { "API blocking filter initialized" }
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        log.info { "API is offline. Request blocked: ${request.method} ${request.requestURI}" }
        response.status = HttpServletResponse.SC_SERVICE_UNAVAILABLE
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        ALLOWED_PATHS.any { request.requestURI.matches(it.toRegex()) }

    companion object {
        val ALLOWED_PATHS =
            setOf(
                "/actuator/health/.*",
                "/status",
            )
    }
}
