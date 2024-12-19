package fi.hel.haitaton.hanke.profiili

import fi.hel.haitaton.hanke.security.AmrValues
import fi.hel.haitaton.hanke.security.JwtClaims
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service

@Service
class ProfiiliService(private val profiiliClient: ProfiiliClient) {

    fun getVerifiedName(securityContext: SecurityContext): Names {
        val credentials =
            securityContext.authentication?.let { it.credentials as Jwt }
                ?: throw VerifiedNameNotFound("User not authenticated.")

        val amr = credentials.getClaim<List<String>>(JwtClaims.AMR)
        return if (amr.contains(AmrValues.SUOMI_FI)) {
            profiiliClient.getVerifiedName(credentials.tokenValue)
        } else if (amr.contains(AmrValues.AD)) {
            nameFromToken(credentials)
        } else {
            throw AuthenticationMethodNotSupported(amr)
        }
    }

    private fun nameFromToken(credentials: Jwt): Names {
        val given: String =
            credentials.getClaim(JwtClaims.GIVEN_NAME)
                ?: throw NameClaimNotFound(JwtClaims.GIVEN_NAME)
        val family: String =
            credentials.getClaim(JwtClaims.FAMILY_NAME)
                ?: throw NameClaimNotFound(JwtClaims.FAMILY_NAME)
        return Names(given, family, given)
    }
}

class NameClaimNotFound(claim: String) :
    RuntimeException(
        "Claim $claim not found from token even though the token is with Helsinki AD authentication."
    )

class AuthenticationMethodNotSupported(amr: List<String>?) :
    RuntimeException("Authentication method not supported: $amr")
