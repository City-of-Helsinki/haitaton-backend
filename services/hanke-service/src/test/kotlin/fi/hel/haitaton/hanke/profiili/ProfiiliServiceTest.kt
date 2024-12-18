package fi.hel.haitaton.hanke.profiili

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.messageContains
import assertk.assertions.prop
import fi.hel.haitaton.hanke.factory.ProfiiliFactory
import fi.hel.haitaton.hanke.security.AmrValues
import fi.hel.haitaton.hanke.security.JwtClaims
import fi.hel.haitaton.hanke.test.AuthenticationMocks
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.oauth2.jwt.Jwt

class ProfiiliServiceTest {

    private val securityContext: SecurityContext = mockk()
    private val profiiliClient: ProfiiliClient = mockk()

    private val profiiliService = ProfiiliService(profiiliClient)

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(securityContext, profiiliClient)
    }

    @Nested
    inner class GetVerifiedName {
        @Test
        fun `throws exception when no authentication is found`() {
            every { securityContext.authentication } returns null

            val failure = assertFailure { profiiliService.getVerifiedName(securityContext) }

            failure.all {
                hasClass(VerifiedNameNotFound::class)
                hasMessage("Verified name of user could not be obtained. User not authenticated.")
            }
            verifySequence { securityContext.authentication }
        }

        @Test
        fun `throws exception when authentication has no credentials`() {
            val authentication: Authentication = mockk()
            every { securityContext.authentication } returns authentication
            every { authentication.credentials } returns null

            val failure = assertFailure { profiiliService.getVerifiedName(securityContext) }

            failure.all {
                hasClass(NullPointerException::class)
                hasMessage(
                    "null cannot be cast to non-null type org.springframework.security.oauth2.jwt.Jwt"
                )
            }
            verifySequence { securityContext.authentication }
        }

        @Test
        fun `gets name from Profiili when amr claim says the user authenticated with Suomi fi`() {
            val authentication = AuthenticationMocks.suomiFiAuthentication()
            every { securityContext.authentication } returns authentication
            val token = AuthenticationMocks.TOKEN_VALUE
            every { profiiliClient.getVerifiedName(token) } returns ProfiiliFactory.DEFAULT_NAMES

            val response = profiiliService.getVerifiedName(securityContext)

            assertThat(response).isEqualTo(ProfiiliFactory.DEFAULT_NAMES)
            verifySequence {
                securityContext.authentication
                profiiliClient.getVerifiedName(token)
            }
        }

        @Test
        fun `propagates the exception when ProfiiliClient throws an exception`() {
            val authentication = AuthenticationMocks.suomiFiAuthentication()
            every { securityContext.authentication } returns authentication
            val token = AuthenticationMocks.TOKEN_VALUE
            val message = "Token response did not contain an access token."
            every { profiiliClient.getVerifiedName(token) } throws VerifiedNameNotFound(message)

            val failure = assertFailure { profiiliService.getVerifiedName(securityContext) }

            failure.all {
                hasClass(VerifiedNameNotFound::class)
                messageContains(message)
            }
            verifySequence {
                securityContext.authentication
                profiiliClient.getVerifiedName(token)
            }
        }

        @Test
        fun `returns the name from the access token when user is authenticated with Helsinki AD`() {
            val authentication = AuthenticationMocks.adAuthentication()
            every { securityContext.authentication } returns authentication

            val response = profiiliService.getVerifiedName(securityContext)

            assertThat(response).all {
                prop(Names::firstName).isEqualTo(ProfiiliFactory.DEFAULT_GIVEN_NAME)
                prop(Names::lastName).isEqualTo(ProfiiliFactory.DEFAULT_LAST_NAME)
                prop(Names::givenName).isEqualTo(ProfiiliFactory.DEFAULT_GIVEN_NAME)
            }
            verifySequence {
                securityContext.authentication
                profiiliClient wasNot Called
            }
        }

        @Test
        fun `throws an exception when given name not found in token`() {
            val jwt =
                Jwt.withTokenValue(AuthenticationMocks.TOKEN_VALUE)
                    .header("alg", "none")
                    .claim(JwtClaims.AMR, listOf(AmrValues.AD))
                    .claim(JwtClaims.FAMILY_NAME, ProfiiliFactory.DEFAULT_LAST_NAME)
                    .build()
            val authentication: Authentication = mockk()
            every { authentication.credentials } returns jwt
            every { securityContext.authentication } returns authentication

            val failure = assertFailure { profiiliService.getVerifiedName(securityContext) }

            failure.all {
                hasClass(NameClaimNotFound::class)
                hasMessage(
                    "Claim given_name not found from token even though the token is with Helsinki AD authentication."
                )
            }
            verifySequence {
                securityContext.authentication
                profiiliClient wasNot Called
            }
        }

        @Test
        fun `throws an exception when family name not found in token`() {
            val jwt =
                Jwt.withTokenValue(AuthenticationMocks.TOKEN_VALUE)
                    .header("alg", "none")
                    .claim(JwtClaims.AMR, listOf(AmrValues.AD))
                    .claim(JwtClaims.GIVEN_NAME, ProfiiliFactory.DEFAULT_GIVEN_NAME)
                    .build()
            val authentication: Authentication = mockk()
            every { authentication.credentials } returns jwt
            every { securityContext.authentication } returns authentication

            val failure = assertFailure { profiiliService.getVerifiedName(securityContext) }

            failure.all {
                hasClass(NameClaimNotFound::class)
                hasMessage(
                    "Claim family_name not found from token even though the token is with Helsinki AD authentication."
                )
            }
            verifySequence {
                securityContext.authentication
                profiiliClient wasNot Called
            }
        }
    }
}
