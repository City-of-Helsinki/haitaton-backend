package fi.hel.haitaton.hanke.profiili

import org.springframework.security.core.context.SecurityContext
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service

@Service
class ProfiiliService(private val profiiliClient: ProfiiliClient) {

    fun getVerifiedName(securityContext: SecurityContext): Names {
        val credentials =
            securityContext.authentication?.let { it.credentials as Jwt }
                ?: throw VerifiedNameNotFound("User not authenticated.")

        val amr = credentials.getClaim<List<String>>(AMR_CLAIM)
        return if (amr.contains(SUOMI_FI_AMR_TAG)) {
            profiiliClient.getVerifiedName(credentials.tokenValue)
        } else if (amr.contains(AD_AMR_TAG)) {
            nameFromToken(credentials)
        } else {
            throw AuthenticationMethodNotSupported(amr)
        }
    }

    private fun nameFromToken(credentials: Jwt): Names {
        val given: String =
            credentials.getClaim(GIVEN_NAME_CLAIM) ?: throw NameClaimNotFound(GIVEN_NAME_CLAIM)
        val family: String =
            credentials.getClaim(FAMILY_NAME_CLAIM) ?: throw NameClaimNotFound(FAMILY_NAME_CLAIM)
        return Names(given, family, given)
    }

    companion object {
        const val AMR_CLAIM = "amr"
        const val GIVEN_NAME_CLAIM = "given_name"
        const val FAMILY_NAME_CLAIM = "family_name"
        const val SUOMI_FI_AMR_TAG = "suomi_fi"
        const val AD_AMR_TAG = "helsinkiad"
    }
}

class NameClaimNotFound(claim: String) :
    RuntimeException(
        "Claim $claim not found from token even though the token is with Helsinki AD authentication."
    )

class AuthenticationMethodNotSupported(amr: List<String>?) :
    RuntimeException("Authentication method not supported: $amr")
