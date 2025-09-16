package fi.hel.haitaton.hanke.security

import fi.hel.haitaton.hanke.gdpr.GdprProperties
import mu.KotlinLogging
import mu.withLoggingContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.annotation.Order
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtDecoders
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.web.SecurityFilterChain

private val logger = KotlinLogging.logger {}

@Configuration
@EnableMethodSecurity(prePostEnabled = true)
class OAuth2ResourceServerSecurityConfiguration(
    private val gdprProperties: GdprProperties,
    private val adFilterProperties: AdFilterProperties,
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private val issuerUri: String,
    @Value("\${spring.security.oauth2.resourceserver.jwt.audiences}") private val audience: String,
    private val tokenValidator: LogoutTokenValidator,
) {

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        AccessRules.configureHttpAccessRules(http)
        http.oauth2ResourceServer { _ -> defaultJwtDecoder() }

        http.securityMatcher("/backchannel-logout").oauth2ResourceServer {
            it.jwt { jwt -> jwt.decoder(logoutJwtDecoder()) }
        }

        return http.build()
    }

    /**
     * Custom decoder that verifies the AD groups and audience of the token on top of the default
     * behaviour.
     *
     * Adopted from:
     * https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html#oauth2resourceserver-jwt-validation-custom
     */
    @Bean("defaultJwtDecoder")
    @Primary
    fun defaultJwtDecoder(): JwtDecoder {
        val jwtDecoder = JwtDecoders.fromIssuerLocation(issuerUri) as NimbusJwtDecoder

        val adGroupValidator = AdGroupValidator(adFilterProperties)
        val audienceValidator =
            JwtClaimValidator<List<String>>(JwtClaimNames.AUD) { aud -> aud.contains(audience) }
        val defaultValidator = JwtValidators.createDefaultWithIssuer(issuerUri)
        val combinedValidator =
            DelegatingOAuth2TokenValidator(defaultValidator, audienceValidator, adGroupValidator)

        jwtDecoder.setJwtValidator(combinedValidator)

        return jwtDecoder
    }

    /**
     * Custom decoder for logout tokens that verifies the claims required for backchannel logout.
     *
     * This is used for the backchannel logout endpoint.
     */
    @Bean("logoutJwtDecoder")
    fun logoutJwtDecoder(): JwtDecoder {
        val jwtDecoder: NimbusJwtDecoder =
            JwtDecoders.fromIssuerLocation(issuerUri) as NimbusJwtDecoder

        val issuerValidator: OAuth2TokenValidator<Jwt> =
            JwtValidators.createDefaultWithIssuer(issuerUri)

        val validator: OAuth2TokenValidator<Jwt> =
            DelegatingOAuth2TokenValidator(issuerValidator, tokenValidator)

        jwtDecoder.setJwtValidator(validator)

        return jwtDecoder
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
            .oauth2ResourceServer { it.jwt { jwt -> jwt.decoder(gdprJwtDecoder()) } }
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

    fun gdprAudienceValidator(): OAuth2TokenValidator<Jwt?> =
        JwtClaimValidator<List<String>>(JwtClaimNames.AUD) { aud ->
            aud.contains(gdprProperties.audience)
        }

    /** Custom decoder needed to check the audience. */
    fun gdprJwtDecoder(): JwtDecoder {
        val jwtDecoder: NimbusJwtDecoder =
            JwtDecoders.fromIssuerLocation(gdprProperties.issuer) as NimbusJwtDecoder

        val audienceValidator = gdprAudienceValidator()
        val withIssuer: OAuth2TokenValidator<Jwt> =
            JwtValidators.createDefaultWithIssuer(gdprProperties.issuer)
        val withAudience: OAuth2TokenValidator<Jwt> =
            DelegatingOAuth2TokenValidator(withIssuer, audienceValidator)

        jwtDecoder.setJwtValidator(withAudience)

        return jwtDecoder
    }

    class AdGroupValidator(private val adFilterProperties: AdFilterProperties) :
        OAuth2TokenValidator<Jwt> {
        override fun validate(jwt: Jwt): OAuth2TokenValidatorResult =
            if (jwt.getClaimAsStringList(JwtClaims.AMR).contains(AmrValues.SUOMI_FI)) {
                OAuth2TokenValidatorResult.success()
            } else if (jwt.getClaimAsStringList(JwtClaims.AMR).contains(AmrValues.AD)) {
                validateAdGroups(jwt).let { if (it.hasErrors()) it else validateNames(jwt) }
            } else {
                val error =
                    OAuth2Error(
                        "invalid_token",
                        "The AMR claim has no recognized authentication methods",
                        null,
                    )
                OAuth2TokenValidatorResult.failure(error)
            }

        private fun validateAdGroups(jwt: Jwt): OAuth2TokenValidatorResult {
            val groups = jwt.getClaimAsStringList("ad_groups").toSet()

            return if (!adFilterProperties.use) {
                OAuth2TokenValidatorResult.success()
            } else if (adFilterProperties.allowedGroups.any { groups.contains(it) }) {
                OAuth2TokenValidatorResult.success()
            } else {
                val error = OAuth2Error("invalid_token", "No allowed AD groups", null)
                OAuth2TokenValidatorResult.failure(error)
            }
        }

        private fun validateNames(jwt: Jwt): OAuth2TokenValidatorResult {
            // An empty error list is considered a success.
            val errors =
                checkClaim(jwt, JwtClaims.GIVEN_NAME) + checkClaim(jwt, JwtClaims.FAMILY_NAME)
            return OAuth2TokenValidatorResult.failure(errors)
        }

        private fun checkClaim(jwt: Jwt, claim: String): List<OAuth2Error> =
            if (jwt.getClaimAsString(claim).isNullOrBlank()) {
                withLoggingContext("userId" to jwt.subject) {
                    logger.error {
                        "Claim $claim not found from token even though the token is with Helsinki AD authentication."
                    }
                }
                listOf(OAuth2Error("invalid_token", "Missing $claim", null))
            } else {
                listOf()
            }
    }
}
