package fi.hel.haitaton.hanke.verifiedname

import fi.hel.haitaton.hanke.security.AmrValues
import fi.hel.haitaton.hanke.security.JwtClaims
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service

@Service
class VerifiedNameService {

    fun getVerifiedName(securityContext: SecurityContext): Names {
        val credentials =
            securityContext.authentication?.let { it.credentials as Jwt }
                ?: throw VerifiedNameNotFound("User not authenticated.")

        val amr = credentials.getClaim<List<String>>(JwtClaims.AMR)
        if (amr != null && (amr.contains(AmrValues.SUOMI_FI) || amr.contains(AmrValues.AD))) {
            return nameFromToken(credentials)
        }
        throw AuthenticationMethodNotSupported(amr)
    }

    private fun nameFromToken(credentials: Jwt): Names {
        val given: String =
            credentials.getClaim<String>(JwtClaims.GIVEN_NAME)?.ifBlank { null }
                ?: throw NameClaimNotFound(JwtClaims.GIVEN_NAME)
        val family: String =
            credentials.getClaim<String>(JwtClaims.FAMILY_NAME)?.ifBlank { null }
                ?: throw NameClaimNotFound(JwtClaims.FAMILY_NAME)
        return Names(given, family, given)
    }
}

/**
 * firstName and givenName are always identical now: the JWT only carries a single given_name
 * claim, not the full set of a person's first names that DVV data used to provide via Profiili.
 */
data class Names(val firstName: String, val lastName: String, val givenName: String)

class VerifiedNameNotFound(reason: String) :
    RuntimeException("Verified name of user could not be obtained. $reason")

class NameClaimNotFound(claim: String) : RuntimeException("Claim $claim not found from token.")

class AuthenticationMethodNotSupported(amr: List<String>?) :
    RuntimeException("Authentication method not supported: $amr")
