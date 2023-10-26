package fi.hel.haitaton.hanke.security

import jakarta.servlet.Filter
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletRequest
import jakarta.servlet.ServletResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
class UserIdLoggingFilter : Filter {
    override fun doFilter(
        request: ServletRequest,
        response: ServletResponse?,
        filterChain: FilterChain,
    ) {
        if (request is HttpServletRequest && response is HttpServletResponse) {
            // Nullable for GDPR API with alternative authentication
            val userId = SecurityContextHolder.getContext().authentication.name
            // Application log can use MDC
            MDC.put("userId", userId)
            // Access log's AccessEventCompositeJsonEncoder can't use MDC, so use a request
            // attribute instead
            request.setAttribute("userId", userId)
        }
        filterChain.doFilter(request, response)
    }
}
