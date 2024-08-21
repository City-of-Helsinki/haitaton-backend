package fi.hel.haitaton.hanke.security

import fi.hel.haitaton.hanke.gdpr.GdprProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
class OAuth2ResourceServerSecurityConfiguration(private val gdprProperties: GdprProperties) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        AccessRules.configureHttpAccessRules(http)
        http.oauth2ResourceServer { it.jwt {} }
        return http.build()
    }

    /**
     * Higher priority filter chain for authenticating GDPR API requests.
     *
     * When GDPR API is enabled, i.e. haitaton.gdpr.disabled is false, or missing completely, all
     * requests to the GDPR API endpoints are authenticated by parsing and validating the JWT in the
     * request.
     */
    @Bean
    @Order(1)
    @ConditionalOnProperty(
        name = ["haitaton.gdpr.disabled"],
        havingValue = "false",
        matchIfMissing = true,
    )
    fun gdprFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/gdpr-api/**")
            .authorizeHttpRequests { it.anyRequest().authenticated() }
            .oauth2ResourceServer { it.jwt(Customizer.withDefaults()) }
        return http.build()
    }

    /**
     * When haitaton.gdpr.disabled is true, deny all requests to the GDPR API endpoints.
     *
     * Without this, the authentication token would be sent to the user-info endpoint for
     * validation, which we don't want if it's a JWT of some sort.
     */
    @Bean
    @Order(1)
    @ConditionalOnProperty(name = ["haitaton.gdpr.disabled"], havingValue = "true")
    fun gdprDisabledFilterChain(http: HttpSecurity): SecurityFilterChain {
        http.securityMatcher("/gdpr-api/**").authorizeHttpRequests { authorize ->
            authorize.anyRequest().denyAll()
        }
        return http.build()
    }

    fun audienceValidator(): OAuth2TokenValidator<Jwt?> =
        JwtClaimValidator<List<String>>(JwtClaimNames.AUD) { aud ->
            aud.contains(gdprProperties.audience)
        }

    /** Custom decoder needed to check the audience. */
    @Bean
    @ConditionalOnProperty(
        value = ["haitaton.gdpr.disabled"],
        havingValue = "false",
        matchIfMissing = true,
    )
    fun jwtDecoder(): JwtDecoder {
        val jwtDecoder: NimbusJwtDecoder =
            JwtDecoders.fromIssuerLocation(gdprProperties.issuer) as NimbusJwtDecoder

        val audienceValidator = audienceValidator()
        val withIssuer: OAuth2TokenValidator<Jwt> =
            JwtValidators.createDefaultWithIssuer(gdprProperties.issuer)
        val withAudience: OAuth2TokenValidator<Jwt> =
            DelegatingOAuth2TokenValidator(withIssuer, audienceValidator)

        jwtDecoder.setJwtValidator(withAudience)

        return jwtDecoder
    }
}
