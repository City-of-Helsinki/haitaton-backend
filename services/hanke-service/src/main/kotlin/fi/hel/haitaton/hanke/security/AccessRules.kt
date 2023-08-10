package fi.hel.haitaton.hanke.security

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import io.sentry.Sentry
import javax.servlet.http.HttpServletResponse
import mu.KotlinLogging
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity

private val logger = KotlinLogging.logger {}

object AccessRules {
    fun configureHttpAccessRules(http: HttpSecurity) {
        http
            .authorizeRequests()
            .mvcMatchers(
                HttpMethod.GET,
                "/actuator/health",
                "/actuator/health/liveness",
                "/actuator/health/readiness",
                "/status",
                "/public-hankkeet",
                "/swagger-ui.html",
                "/swagger-ui/**",
                "/v3/api-docs/**",
            )
            .permitAll()
            .mvcMatchers(HttpMethod.POST, "/testdata/unlink-applications")
            .permitAll()
            .and()
            .csrf()
            .ignoringAntMatchers("/testdata/unlink-applications")
            .and()
            .authorizeRequests()
            .anyRequest()
            .authenticated()
            .and()
            .exceptionHandling()
            .authenticationEntryPoint { request, response, authenticationException ->
                logger.warn {
                    "User has to be authenticated " +
                        "to access ${request.method} ${request.requestURI}"
                }
                Sentry.captureException(authenticationException)
                response.writer.print(OBJECT_MAPPER.writeValueAsString(HankeError.HAI0001))
                response.status = HttpServletResponse.SC_UNAUTHORIZED
            }
    }
}
