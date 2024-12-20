package fi.hel.haitaton.hanke.test

import fi.hel.haitaton.hanke.factory.ProfiiliFactory.DEFAULT_GIVEN_NAME
import fi.hel.haitaton.hanke.factory.ProfiiliFactory.DEFAULT_LAST_NAME
import fi.hel.haitaton.hanke.security.AmrValues
import fi.hel.haitaton.hanke.security.JwtClaims
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.oauth2.jwt.Jwt

object AuthenticationMocks {
    const val TOKEN_VALUE = "fake value of the JWT"
    const val ALLOWED_AD_GROUP_1 = "first_allowed"
    const val ALLOWED_AD_GROUP_2 = "second_allowed"
    val DEFAULT_AD_GROUPS = setOf(ALLOWED_AD_GROUP_1, ALLOWED_AD_GROUP_2)

    fun adLoginMock(userId: String = USERNAME): SecurityContext {
        val auth: Authentication = adAuthentication(userId)
        every { auth.name } returns userId
        val securityContext: SecurityContext = mockk()
        every { securityContext.authentication } returns auth

        return securityContext
    }

    fun adAuthentication(userId: String = USERNAME): Authentication {
        val jwt = adJwt(userId)

        val auth: Authentication = mockk()
        every { auth.credentials } returns jwt
        return auth
    }

    fun adJwt(userId: String = USERNAME, adGroups: Collection<String> = DEFAULT_AD_GROUPS): Jwt =
        Jwt.withTokenValue(TOKEN_VALUE)
            .header("alg", "none")
            .subject(userId)
            .claim(JwtClaims.AMR, listOf(AmrValues.AD))
            .claim(JwtClaims.GIVEN_NAME, DEFAULT_GIVEN_NAME)
            .claim(JwtClaims.FAMILY_NAME, DEFAULT_LAST_NAME)
            .claim(JwtClaims.AD_GROUPS, adGroups)
            .build()

    /** When using this, you have to mock ProfiiliClient.getVerifiedName as well. */
    fun suomiFiLoginMock(userId: String = USERNAME): SecurityContext {
        val auth: Authentication = suomiFiAuthentication(userId)
        every { auth.name } returns userId
        val securityContext: SecurityContext = mockk()
        every { securityContext.authentication } returns auth

        return securityContext
    }

    fun suomiFiAuthentication(userId: String = USERNAME): Authentication {
        val jwt = suomiFiJwt(userId)
        val auth: Authentication = mockk(relaxed = true)
        every { auth.credentials } returns jwt
        return auth
    }

    fun suomiFiJwt(userId: String = USERNAME): Jwt =
        Jwt.withTokenValue(TOKEN_VALUE)
            .header("alg", "none")
            .subject(userId)
            .claim(JwtClaims.AMR, listOf(AmrValues.SUOMI_FI))
            .build()
}
