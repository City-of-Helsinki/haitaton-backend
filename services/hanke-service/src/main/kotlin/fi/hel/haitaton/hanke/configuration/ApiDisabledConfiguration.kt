package fi.hel.haitaton.hanke.configuration

import jakarta.servlet.Filter
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@Configuration
@ConditionalOnProperty(
    name = ["haitaton.api.enabled"],
    havingValue = "false",
    matchIfMissing = false
)
class ApiDisabledConfiguration {

    companion object {
        val BLOCKED_PATHS =
            arrayOf(
                "/public-hankkeet",
                "/hankkeet/*",
                "/hakemukset/*",
                "/johtoselvityshakemus",
                "/my-permissions",
                "/kayttajat/*",
                "/profiili/*"
            )
    }

    /**
     * In order to be able to block calls to the API and return 503 Service Unavailable, we need to
     * allow the traffic first.
     */
    @Bean
    @Order(1)
    fun allowTraffic(http: HttpSecurity): SecurityFilterChain {
        http.securityMatcher(*BLOCKED_PATHS).authorizeHttpRequests { it.anyRequest().permitAll() }
        return http.build()
    }

    @Bean
    @Order(1)
    fun apiBlockingFilter(): FilterRegistrationBean<Filter> {
        val registrationBean = FilterRegistrationBean<Filter>()
        registrationBean.filter = Filter { _, response, _ ->
            (response as HttpServletResponse).status = HttpServletResponse.SC_SERVICE_UNAVAILABLE
        }
        registrationBean.addUrlPatterns(*BLOCKED_PATHS)
        registrationBean.order = 0
        return registrationBean
    }
}
