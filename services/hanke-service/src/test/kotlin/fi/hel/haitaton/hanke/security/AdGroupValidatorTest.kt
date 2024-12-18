package fi.hel.haitaton.hanke.security

import assertk.Assert
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.prop
import assertk.assertions.single
import fi.hel.haitaton.hanke.test.AuthenticationMocks
import fi.hel.haitaton.hanke.test.AuthenticationMocks.TOKEN_VALUE
import fi.hel.haitaton.hanke.test.USERNAME
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt

class AdGroupValidatorTest {

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `fails when user is not authenticated with either Suomi fi or AD`(useAdFilter: Boolean) {
        val validator = validator(useAdFilter)
        val jwt =
            Jwt.withTokenValue(TOKEN_VALUE)
                .header("alg", "none")
                .subject(USERNAME)
                .claim(JwtClaims.AMR, listOf("third_method"))
                .build()

        val result = validator.validate(jwt)

        assertThat(result).hasError("The AMR claim has no recognized authentication methods")
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `succeeds when user authenticated with Suomi fi`(useAdFilter: Boolean) {
        val validator = validator(useAdFilter)
        val jwt = AuthenticationMocks.suomiFiJwt()

        val result = validator.validate(jwt)

        assertThat(result).isSuccess()
    }

    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun `succeeds when user has an allowed AD group`(useAdFilter: Boolean) {
        val validator = validator(useAdFilter)
        val jwt = AuthenticationMocks.adJwt(adGroups = AuthenticationMocks.DEFAULT_AD_GROUPS)

        val result = validator.validate(jwt)

        assertThat(result).isSuccess()
    }

    @Nested
    inner class WithFilterEnabled {
        @Test
        fun `fails when the user has no allowed AD group`() {
            val validator = validator(true)
            val jwt = AuthenticationMocks.adJwt(adGroups = setOf("Something", "else"))

            val result = validator.validate(jwt)

            assertThat(result).hasError("No allowed AD groups")
        }
    }

    @Nested
    inner class WithAdFilterDisabled {

        @Test
        fun `succeeds when the user has no allowed AD group`() {
            val validator = validator(false)
            val jwt = AuthenticationMocks.adJwt(adGroups = setOf("Something", "else"))

            val result = validator.validate(jwt)

            assertThat(result).isSuccess()
        }

        @Test
        fun `succeeds when there are no allowed AD groups configured`() {
            val validator = validator(false, allowedGroups = setOf())
            val jwt = AuthenticationMocks.adJwt(adGroups = setOf("Something", "else"))

            val result = validator.validate(jwt)

            assertThat(result).isSuccess()
        }
    }

    private fun validator(
        useAdFilter: Boolean,
        allowedGroups: Set<String> = AuthenticationMocks.DEFAULT_AD_GROUPS,
    ): OAuth2ResourceServerSecurityConfiguration.AdGroupValidator {
        val adFilterProperties = AdFilterProperties(useAdFilter, allowedGroups)
        return OAuth2ResourceServerSecurityConfiguration.AdGroupValidator(adFilterProperties)
    }

    private fun Assert<OAuth2TokenValidatorResult>.isSuccess() {
        prop(OAuth2TokenValidatorResult::getErrors).isEmpty()
        prop(OAuth2TokenValidatorResult::hasErrors).isEqualTo(false)
    }

    private fun Assert<OAuth2TokenValidatorResult>.hasError(f: Assert<OAuth2Error>.() -> Unit) {
        prop(OAuth2TokenValidatorResult::getErrors).single().f()
        prop(OAuth2TokenValidatorResult::hasErrors).isEqualTo(true)
    }

    private fun Assert<OAuth2TokenValidatorResult>.hasError(description: String) {
        hasError {
            prop(OAuth2Error::getUri).isNull()
            prop(OAuth2Error::getErrorCode).isEqualTo("invalid_token")
            prop(OAuth2Error::getDescription).isEqualTo(description)
        }
    }
}
