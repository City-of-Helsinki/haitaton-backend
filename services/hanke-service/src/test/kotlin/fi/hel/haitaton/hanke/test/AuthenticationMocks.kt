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

    fun adLoginMock(userId: String = USERNAME): SecurityContext = mockk {
        every { authentication } returns
            mockk {
                every { credentials } returns adJwt(userId)
                every { name } returns userId
            }
    }

    fun adAuthentication(userId: String = USERNAME): Authentication = mockk {
        every { credentials } returns adJwt(userId)
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
    fun suomiFiLoginMock(userId: String = USERNAME): SecurityContext = mockk {
        every { authentication } returns
            mockk {
                every { credentials } returns suomiFiJwt(userId)
                every { name } returns userId
            }
    }

    fun suomiFiAuthentication(userId: String = USERNAME): Authentication =
        mockk(relaxed = true) { every { credentials } returns suomiFiJwt(userId) }

    fun suomiFiJwt(userId: String = USERNAME): Jwt =
        Jwt.withTokenValue(TOKEN_VALUE)
            .header("alg", "none")
            .subject(userId)
            .claim(JwtClaims.AMR, listOf(AmrValues.SUOMI_FI))
            .build()
}
