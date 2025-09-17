package fi.hel.haitaton.hanke.security

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import io.sentry.Sentry
import jakarta.servlet.http.HttpServletResponse
import mu.KotlinLogging
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.security.config.annotation.web.builders.HttpSecurity

private val logger = KotlinLogging.logger {}

object AccessRules {
    fun configureHttpAccessRules(http: HttpSecurity) {
        http
            .authorizeHttpRequests {
                it.requestMatchers(
                        HttpMethod.GET,
                        "/actuator/health",
                        "/actuator/health/liveness",
                        "/actuator/health/readiness",
                        "/status",
                        "/banners",
                        "/public-hankkeet",
                        "/public-hankkeet/*",
                        "/public-hankkeet/grid/metadata",
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/testdata/trigger-allu",
                    )
                    .permitAll()
                    .requestMatchers(
                        HttpMethod.POST,
                        "/testdata/unlink-applications",
                        "/testdata/create-public-hanke/*",
                        "/public-hankkeet/grid",
                        "/backchannel-logout",
                    )
                    .permitAll()
            }
            .csrf {
                it.ignoringRequestMatchers(
                    "/testdata/unlink-applications",
                    "/testdata/create-public-hanke/*",
                    "/public-hankkeet/grid",
                    "/backchannel-logout",
                )
            }
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .exceptionHandling {
                it.authenticationEntryPoint { request, response, authenticationException ->
                    logger.warn {
                        "User has to be authenticated " +
                            "to access ${request.method} ${request.requestURI}"
                    }
                    Sentry.captureException(authenticationException)
                    response.writer.print(OBJECT_MAPPER.writeValueAsString(HankeError.HAI0001))
                    response.status = HttpServletResponse.SC_UNAUTHORIZED
                    response.contentType = APPLICATION_JSON_VALUE
                }
            }
    }
}
