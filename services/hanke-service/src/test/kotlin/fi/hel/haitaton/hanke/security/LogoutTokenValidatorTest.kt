package fi.hel.haitaton.hanke.security

import assertk.Assert
import assertk.assertThat
import assertk.assertions.isEqualTo
import fi.hel.haitaton.hanke.security.LogoutTokenValidator.Companion.BACKCHANNEL_LOGOUT_EVENT
import java.time.Instant
import org.junit.jupiter.api.Test
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt

class LogoutTokenValidatorTest {

    private val validator = LogoutTokenValidator("haitaton")

    @Test
    fun `returns success when token is valid`() {
        val jwt = stubJwt()

        val result = validator.validate(jwt)

        assertThat(result).isEqualTo(OAuth2TokenValidatorResult.success())
    }

    @Test
    fun `returns error if iss claim is missing`() {
        val jwt = stubJwt(iss = null)

        val result = validator.validate(jwt)

        assertThat(result)
            .hasErrorWithCodeAndDescription("invalid_token", "Missing issuer claim in logout token")
    }

    @Test
    fun `returns error if aud claim is missing`() {
        val jwt = stubJwt(aud = null)

        val result = validator.validate(jwt)

        assertThat(result)
            .hasErrorWithCodeAndDescription(
                "invalid_token",
                "Missing audience claim in logout token",
            )
    }

    @Test
    fun `returns error if iat claim is missing`() {
        val jwt = stubJwt(iat = null)

        val result = validator.validate(jwt)

        assertThat(result)
            .hasErrorWithCodeAndDescription(
                "invalid_token",
                "Missing issued at claim in logout token",
            )
    }

    @Test
    fun `returns error if exp claim is missing`() {
        val jwt = stubJwt(exp = null)

        val result = validator.validate(jwt)

        assertThat(result)
            .hasErrorWithCodeAndDescription(
                "invalid_token",
                "Missing expires at claim in logout token",
            )
    }

    @Test
    fun `returns error if jti claim is missing`() {
        val jwt = stubJwt(jti = null)

        val result = validator.validate(jwt)

        assertThat(result)
            .hasErrorWithCodeAndDescription("invalid_token", "Missing id claim in logout token")
    }

    @Test
    fun `throws exception if events claim is missing`() {
        val jwt = stubJwt(events = null)

        val result = validator.validate(jwt)

        assertThat(result)
            .hasErrorWithCodeAndDescription("invalid_token", "Missing events claim in logout token")
    }

    @Test
    fun `returns error if events claim does not have correct key`() {
        val jwt = stubJwt(events = mapOf("wrong-key" to "{}"))

        val result = validator.validate(jwt)

        assertThat(result)
            .hasErrorWithCodeAndDescription(
                "invalid_token",
                "Missing backchannel logout event in events claim",
            )
    }

    @Test
    fun `returns error if events claim does not have correct value`() {
        val jwt = stubJwt(events = mapOf(BACKCHANNEL_LOGOUT_EVENT to "invalid-value"))

        val result = validator.validate(jwt)

        assertThat(result)
            .hasErrorWithCodeAndDescription(
                "invalid_token",
                "Invalid backchannel logout event in events claim: invalid-value",
            )
    }

    @Test
    fun `throws exception if both sub and sid claims are missing`() {
        val jwt = stubJwt(sub = null, sid = null)

        val result = validator.validate(jwt)

        assertThat(result)
            .hasErrorWithCodeAndDescription(
                "invalid_token",
                "Logout token must contain 'sid' or 'sub' claim",
            )
    }

    @Test
    fun `throws exception if nonce claim is present`() {
        val jwt = stubJwt(nonce = "nonce")

        val result = validator.validate(jwt)

        assertThat(result)
            .hasErrorWithCodeAndDescription(
                "invalid_token",
                "Logout token must not contain 'nonce' claim",
            )
    }

    private fun stubJwt(
        iss: String? = "https://server.example.com",
        sub: String? = "subject",
        aud: List<String>? = listOf("haitaton"),
        iat: Instant? = Instant.now(),
        exp: Instant? = Instant.now().plusSeconds(60),
        jti: String? = "id",
        events: Map<String, Any>? = mapOf(BACKCHANNEL_LOGOUT_EVENT to "{}"),
        sid: String? = "session-id",
        nonce: String? = null,
    ): Jwt =
        Jwt.withTokenValue("token")
            .header("alg", "RS256")
            .apply {
                iss?.let { claim("iss", it) }
                sub?.let { claim("sub", it) }
                aud?.let { claim("aud", it) }
                iat?.let { claim("iat", it) }
                exp?.let { claim("exp", it) }
                jti?.let { claim("jti", it) }
                events?.let { claim("events", it) }
                sid?.let { claim("sid", it) }
                nonce?.let { claim("nonce", it) }
            }
            .build()

    private fun Assert<OAuth2TokenValidatorResult>.hasErrorWithCodeAndDescription(
        code: String,
        description: String,
    ) = given { actual ->
        assertThat(actual.hasErrors()).isEqualTo(true)
        val error = actual.errors.single()
        assertThat(error.errorCode).isEqualTo(code)
        assertThat(error.description).isEqualTo(description)
    }
}
