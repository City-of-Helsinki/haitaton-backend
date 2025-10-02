package fi.hel.haitaton.hanke.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.stereotype.Component

@Component
class LogoutTokenValidator(
    @Value("\${spring.security.oauth2.resourceserver.jwt.audiences}") private val audience: String
) : OAuth2TokenValidator<Jwt> {

    companion object {
        const val BACKCHANNEL_LOGOUT_EVENT = "http://schemas.openid.net/event/backchannel-logout"
    }

    /**
     * Validates the logout token. It checks if the token is valid and contains the required claims.
     * See https://openid.net/specs/openid-connect-backchannel-1_0.html#LogoutToken
     *
     * @return Pair of session id (sid) and subject id (sub)
     * @throws JwtException if the token is invalid or missing required claims
     */
    override fun validate(token: Jwt): OAuth2TokenValidatorResult {
        // iss - REQUIRED
        token.issuer?.toString() ?: return failure("Missing issuer claim in logout token")
        // sub - OPTIONAL
        val sub: String? = token.subject
        // aud - REQUIRED
        token.audience?.toString() ?: return failure("Missing audience claim in logout token")
        if (!token.audience.contains(audience)) {
            return failure("The required audience '$audience' is missing in logout token")
        }
        // iat - REQUIRED
        token.issuedAt ?: return failure("Missing issued at claim in logout token")
        // exp - REQUIRED
        token.expiresAt ?: return failure("Missing expires at claim in logout token")
        // jti - REQUIRED
        token.id ?: return failure("Missing id claim in logout token")
        // events - REQUIRED
        val events =
            token.getClaim<Map<String, Any>>("events")
                ?: return failure("Missing events claim in logout token")
        if (!events.containsKey(BACKCHANNEL_LOGOUT_EVENT)) {
            return failure("Missing backchannel logout event in events claim")
        }
        // "The corresponding member value MUST be a JSON object and SHOULD be the empty JSON object
        // {}"
        // Keycloak sends this as an actual empty map {}, not a string "{}"
        val eventValue = events[BACKCHANNEL_LOGOUT_EVENT]
        if (eventValue !is Map<*, *>) {
            return failure(
                "Invalid backchannel logout event in events claim: $eventValue (expected a JSON object)"
            )
        }
        // sid - OPTIONAL
        val sid: String? = token.getClaim<String?>("sid")
        // "A Logout Token MUST contain either a sub or a sid Claim, and MAY contain both"
        if (sid == null && sub == null) {
            return failure("Logout token must contain 'sid' or 'sub' claim")
        }
        // nonce - PROHIBITED
        if (token.getClaim<String?>("nonce") != null) {
            return failure("Logout token must not contain 'nonce' claim")
        }

        return OAuth2TokenValidatorResult.success()
    }

    private fun failure(message: String) =
        OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", message, null))
}
