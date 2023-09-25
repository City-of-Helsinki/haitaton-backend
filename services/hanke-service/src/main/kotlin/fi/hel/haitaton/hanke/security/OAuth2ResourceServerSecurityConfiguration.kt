package fi.hel.haitaton.hanke.security

import fi.hel.haitaton.hanke.gdpr.GdprProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.ParameterizedTypeReference
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.reactive.function.client.WebClient

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
class OAuth2ResourceServerSecurityConfiguration(
    @Value("\${security.oauth2.resource.user-info-uri}") private val userinfoUri: String,
    private val gdprProperties: GdprProperties,
) {
    @Bean
    fun introspector(): OpaqueTokenIntrospector {
        return UserInfoOpaqueTokenIntrospector(userinfoUri)
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        AccessRules.configureHttpAccessRules(http)
        http.oauth2ResourceServer { resourceServer ->
            resourceServer.opaqueToken { opaqueToken -> opaqueToken.introspector(introspector()) }
        }
        return http.build()
    }

    /**
     * Higher priority filter chain for authenticating GDPR API requests.
     *
     * When GDPR API is enabled, i.e. haitaton.gdpr.disabled is false, all requests to the GDPR API
     * endpoints are authenticated by parsing and validating the JWT in the request.
     */
    @Bean
    @Order(1)
    @ConditionalOnProperty(name = ["haitaton.gdpr.disabled"], havingValue = "false")
    fun gdprFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .securityMatcher("/gdpr-api/**")
            .authorizeHttpRequests { authorize -> authorize.anyRequest().authenticated() }
            .oauth2ResourceServer { resourceServer -> resourceServer.jwt() }
        return http.build()
    }

    /**
     * When haitaton.gdpr.disabled is something other than false, or missing completely, deny all
     * requests to the GDPR API endpoints.
     *
     * Without this, the authentication token would be sent to the user-info endpoint for
     * validation, which we don't want if it's a JWT of some sort.
     */
    @Bean
    @Order(1)
    @ConditionalOnProperty(name = ["haitaton.gdpr.disabled"], matchIfMissing = true)
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
    @ConditionalOnProperty(value = ["haitaton.gdpr.disabled"], havingValue = "false")
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

class UserInfoOpaqueTokenIntrospector(private val userinfoUri: String) : OpaqueTokenIntrospector {
    private val rest: WebClient = WebClient.create()

    override fun introspect(token: String): OAuth2AuthenticatedPrincipal {
        val attributes =
            rest
                .get()
                .uri(userinfoUri)
                .headers { it.setBearerAuth(token) }
                .retrieve()
                .bodyToMono(object : ParameterizedTypeReference<Map<String, Any>>() {})
                .block()

        return DefaultOAuth2User(listOf(), attributes, "sub")
    }
}
