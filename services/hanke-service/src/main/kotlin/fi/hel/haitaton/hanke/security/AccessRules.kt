package fi.hel.haitaton.hanke.security

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import javax.servlet.http.HttpServletResponse
import mu.KotlinLogging
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity

private val logger = KotlinLogging.logger { }

object AccessRules {
    fun configureHttpAccessRules(http: HttpSecurity) {
        http.anonymous().and()
            .authorizeRequests()
            .mvcMatchers(HttpMethod.GET, "/organisaatiot").permitAll()
            .mvcMatchers(HttpMethod.POST, "/hankkeet", "/hankkeet/**").hasRole("haitaton-user")
            .mvcMatchers(HttpMethod.GET, "/hankkeet", "/hankkeet/**").hasRole("haitaton-user")
            .mvcMatchers(HttpMethod.PUT, "/hankkeet/**").hasRole("haitaton-user")
            .and().exceptionHandling().accessDeniedHandler { request, response, _ ->
                logger.warn {
                    "User ${request.userPrincipal?.name} is not authorized " +
                            "to access ${request.method} ${request.requestURI}"
                }
                response.writer.print(OBJECT_MAPPER.writeValueAsString(HankeError.HAI0001))
                response.status = HttpServletResponse.SC_FORBIDDEN
            }.authenticationEntryPoint { request, response, _ ->
                logger.warn {
                    "User has to be authenticated " +
                            "to access ${request.method} ${request.requestURI}"
                }
                response.writer.print(OBJECT_MAPPER.writeValueAsString(HankeError.HAI0001))
                response.status = HttpServletResponse.SC_UNAUTHORIZED
            }
    }
}
