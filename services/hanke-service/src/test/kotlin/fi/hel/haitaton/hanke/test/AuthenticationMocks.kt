package fi.hel.haitaton.hanke.test

import fi.hel.haitaton.hanke.factory.ProfiiliFactory.DEFAULT_GIVEN_NAME
import fi.hel.haitaton.hanke.factory.ProfiiliFactory.DEFAULT_LAST_NAME
import fi.hel.haitaton.hanke.profiili.ProfiiliService
import io.mockk.every
import io.mockk.mockk
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.oauth2.jwt.Jwt

object AuthenticationMocks {
    const val TOKEN_VALUE = "fake value of the JWT"

    fun adLoginMock(userId: String = USERNAME): SecurityContext {
        val auth: Authentication = adAuthentication(userId)
        every { auth.name } returns userId
        val securityContext: SecurityContext = mockk()
        every { securityContext.authentication } returns auth

        return securityContext
    }

    fun adAuthentication(userId: String = USERNAME): Authentication {
        val jwt =
            Jwt.withTokenValue(TOKEN_VALUE)
                .header("alg", "none")
                .subject(userId)
                .claim(ProfiiliService.AMR_CLAIM, listOf("helsinkiad"))
                .claim(ProfiiliService.GIVEN_NAME_CLAIM, DEFAULT_GIVEN_NAME)
                .claim(ProfiiliService.FAMILY_NAME_CLAIM, DEFAULT_LAST_NAME)
                .build()

        val auth: Authentication = mockk()
        every { auth.credentials } returns jwt
        return auth
    }

    /** When using this, you have to mock ProfiiliClient.getVerifiedName as well. */
    fun suomiFiLoginMock(userId: String = USERNAME): SecurityContext {
        val auth: Authentication = suomiFiAuthentication(userId)
        every { auth.name } returns userId
        val securityContext: SecurityContext = mockk()
        every { securityContext.authentication } returns auth

        return securityContext
    }

    fun suomiFiAuthentication(userId: String = USERNAME): Authentication {
        val jwt =
            Jwt.withTokenValue(TOKEN_VALUE)
                .header("alg", "none")
                .subject(userId)
                .claim(ProfiiliService.AMR_CLAIM, listOf("suomi_fi"))
                .build()

        val auth: Authentication = mockk(relaxed = true)
        every { auth.credentials } returns jwt
        return auth
    }
}
