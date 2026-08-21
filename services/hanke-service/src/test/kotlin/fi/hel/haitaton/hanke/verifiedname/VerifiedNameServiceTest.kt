package fi.hel.haitaton.hanke.verifiedname

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import fi.hel.haitaton.hanke.factory.VerifiedNameFactory
import fi.hel.haitaton.hanke.security.AmrValues
import fi.hel.haitaton.hanke.security.JwtClaims
import fi.hel.haitaton.hanke.test.AuthenticationMocks
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
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.oauth2.jwt.Jwt

class VerifiedNameServiceTest {

    private val securityContext: SecurityContext = mockk()

    private val verifiedNameService = VerifiedNameService()

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(securityContext)
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    inner class GetVerifiedName {
        @Test
        fun `throws exception when no authentication is found`() {
            every { securityContext.authentication } returns null

            val failure = assertFailure { verifiedNameService.getVerifiedName(securityContext) }

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

            val failure = assertFailure { verifiedNameService.getVerifiedName(securityContext) }

            failure.all {
                hasClass(NullPointerException::class)
                hasMessage(
                    "null cannot be cast to non-null type org.springframework.security.oauth2.jwt.Jwt"
                )
            }
            verifySequence { securityContext.authentication }
        }

        @Test
        fun `returns the name from the access token when user is authenticated with Suomi fi`() {
            val authentication = AuthenticationMocks.suomiFiAuthentication()
            every { securityContext.authentication } returns authentication

            val response = verifiedNameService.getVerifiedName(securityContext)

            assertThat(response).all {
                prop(Names::firstName).isEqualTo(VerifiedNameFactory.DEFAULT_GIVEN_NAME)
                prop(Names::lastName).isEqualTo(VerifiedNameFactory.DEFAULT_LAST_NAME)
                prop(Names::givenName).isEqualTo(VerifiedNameFactory.DEFAULT_GIVEN_NAME)
            }
            verifySequence { securityContext.authentication }
        }

        @Test
        fun `returns the name from the access token when user is authenticated with Helsinki AD`() {
            val authentication = AuthenticationMocks.adAuthentication()
            every { securityContext.authentication } returns authentication

            val response = verifiedNameService.getVerifiedName(securityContext)

            assertThat(response).all {
                prop(Names::firstName).isEqualTo(VerifiedNameFactory.DEFAULT_GIVEN_NAME)
                prop(Names::lastName).isEqualTo(VerifiedNameFactory.DEFAULT_LAST_NAME)
                prop(Names::givenName).isEqualTo(VerifiedNameFactory.DEFAULT_GIVEN_NAME)
            }
            verifySequence { securityContext.authentication }
        }

        @Test
        fun `throws exception when authentication method is not supported`() {
            val jwt =
                Jwt.withTokenValue(AuthenticationMocks.TOKEN_VALUE)
                    .header("alg", "none")
                    .claim(JwtClaims.AMR, listOf("some_other_method"))
                    .build()
            val authentication: Authentication = mockk()
            every { authentication.credentials } returns jwt
            every { securityContext.authentication } returns authentication

            val failure = assertFailure { verifiedNameService.getVerifiedName(securityContext) }

            failure.all {
                hasClass(AuthenticationMethodNotSupported::class)
                hasMessage("Authentication method not supported: [some_other_method]")
            }
            verifySequence { securityContext.authentication }
        }

        @ParameterizedTest
        @MethodSource("missingNameCases")
        fun `throws an exception when given name not found in the token`(
            amr: String,
            givenName: String?,
        ) {
            val builder =
                Jwt.withTokenValue(AuthenticationMocks.TOKEN_VALUE)
                    .header("alg", "none")
                    .claim(JwtClaims.AMR, listOf(amr))
                    .claim(JwtClaims.FAMILY_NAME, VerifiedNameFactory.DEFAULT_LAST_NAME)
            if (givenName != null) builder.claim(JwtClaims.GIVEN_NAME, givenName)
            val jwt = builder.build()
            val authentication: Authentication = mockk()
            every { authentication.credentials } returns jwt
            every { securityContext.authentication } returns authentication

            val failure = assertFailure { verifiedNameService.getVerifiedName(securityContext) }

            failure.all {
                hasClass(NameClaimNotFound::class)
                hasMessage("Claim given_name not found from token.")
            }
            verifySequence { securityContext.authentication }
        }

        @ParameterizedTest
        @MethodSource("missingNameCases")
        fun `throws an exception when family name not found in the token`(
            amr: String,
            familyName: String?,
        ) {
            val builder =
                Jwt.withTokenValue(AuthenticationMocks.TOKEN_VALUE)
                    .header("alg", "none")
                    .claim(JwtClaims.AMR, listOf(amr))
                    .claim(JwtClaims.GIVEN_NAME, VerifiedNameFactory.DEFAULT_GIVEN_NAME)
            if (familyName != null) builder.claim(JwtClaims.FAMILY_NAME, familyName)
            val jwt = builder.build()
            val authentication: Authentication = mockk()
            every { authentication.credentials } returns jwt
            every { securityContext.authentication } returns authentication

            val failure = assertFailure { verifiedNameService.getVerifiedName(securityContext) }

            failure.all {
                hasClass(NameClaimNotFound::class)
                hasMessage("Claim family_name not found from token.")
            }
            verifySequence { securityContext.authentication }
        }

        private fun missingNameCases(): List<Arguments> =
            setOf(null, "", " ", " \t ").flatMap { name ->
                listOf(Arguments.of(AmrValues.AD, name), Arguments.of(AmrValues.SUOMI_FI, name))
            }
    }
}
