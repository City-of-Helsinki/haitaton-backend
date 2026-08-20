package fi.hel.haitaton.hanke.profiili

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import fi.hel.haitaton.hanke.factory.ProfiiliFactory
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
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.oauth2.jwt.Jwt

class ProfiiliServiceTest {

    private val securityContext: SecurityContext = mockk()

    private val profiiliService = ProfiiliService()

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
        fun `returns the name from the access token when user is authenticated with Suomi fi`() {
            val authentication = AuthenticationMocks.suomiFiAuthentication()
            every { securityContext.authentication } returns authentication

            val response = profiiliService.getVerifiedName(securityContext)

            assertThat(response).all {
                prop(Names::firstName).isEqualTo(ProfiiliFactory.DEFAULT_GIVEN_NAME)
                prop(Names::lastName).isEqualTo(ProfiiliFactory.DEFAULT_LAST_NAME)
                prop(Names::givenName).isEqualTo(ProfiiliFactory.DEFAULT_GIVEN_NAME)
            }
            verifySequence { securityContext.authentication }
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

            val failure = assertFailure { profiiliService.getVerifiedName(securityContext) }

            failure.all {
                hasClass(AuthenticationMethodNotSupported::class)
                hasMessage("Authentication method not supported: [some_other_method]")
            }
            verifySequence { securityContext.authentication }
        }

        @ParameterizedTest
        @ValueSource(strings = [" ", " \t "])
        @NullAndEmptySource
        fun `throws an exception when given name not found in an AD token`(givenName: String?) {
            val builder =
                Jwt.withTokenValue(AuthenticationMocks.TOKEN_VALUE)
                    .header("alg", "none")
                    .claim(JwtClaims.AMR, listOf(AmrValues.AD))
                    .claim(JwtClaims.FAMILY_NAME, ProfiiliFactory.DEFAULT_LAST_NAME)
            if (givenName != null) builder.claim(JwtClaims.GIVEN_NAME, givenName)
            val jwt = builder.build()
            val authentication: Authentication = mockk()
            every { authentication.credentials } returns jwt
            every { securityContext.authentication } returns authentication

            val failure = assertFailure { profiiliService.getVerifiedName(securityContext) }

            failure.all {
                hasClass(NameClaimNotFound::class)
                hasMessage("Claim given_name not found from token.")
            }
            verifySequence { securityContext.authentication }
        }

        @ParameterizedTest
        @ValueSource(strings = [" ", " \t "])
        @NullAndEmptySource
        fun `throws an exception when family name not found in an AD token`(familyName: String?) {
            val builder =
                Jwt.withTokenValue(AuthenticationMocks.TOKEN_VALUE)
                    .header("alg", "none")
                    .claim(JwtClaims.AMR, listOf(AmrValues.AD))
                    .claim(JwtClaims.GIVEN_NAME, ProfiiliFactory.DEFAULT_GIVEN_NAME)
            if (familyName != null) builder.claim(JwtClaims.FAMILY_NAME, familyName)
            val jwt = builder.build()
            val authentication: Authentication = mockk()
            every { authentication.credentials } returns jwt
            every { securityContext.authentication } returns authentication

            val failure = assertFailure { profiiliService.getVerifiedName(securityContext) }

            failure.all {
                hasClass(NameClaimNotFound::class)
                hasMessage("Claim family_name not found from token.")
            }
            verifySequence { securityContext.authentication }
        }

        @ParameterizedTest
        @ValueSource(strings = [" ", " \t "])
        @NullAndEmptySource
        fun `throws an exception when given name not found in a Suomi fi token`(
            givenName: String?
        ) {
            val builder =
                Jwt.withTokenValue(AuthenticationMocks.TOKEN_VALUE)
                    .header("alg", "none")
                    .claim(JwtClaims.AMR, listOf(AmrValues.SUOMI_FI))
                    .claim(JwtClaims.FAMILY_NAME, ProfiiliFactory.DEFAULT_LAST_NAME)
            if (givenName != null) builder.claim(JwtClaims.GIVEN_NAME, givenName)
            val jwt = builder.build()
            val authentication: Authentication = mockk()
            every { authentication.credentials } returns jwt
            every { securityContext.authentication } returns authentication

            val failure = assertFailure { profiiliService.getVerifiedName(securityContext) }

            failure.all {
                hasClass(NameClaimNotFound::class)
                hasMessage("Claim given_name not found from token.")
            }
            verifySequence { securityContext.authentication }
        }

        @ParameterizedTest
        @ValueSource(strings = [" ", " \t "])
        @NullAndEmptySource
        fun `throws an exception when family name not found in a Suomi fi token`(
            familyName: String?
        ) {
            val builder =
                Jwt.withTokenValue(AuthenticationMocks.TOKEN_VALUE)
                    .header("alg", "none")
                    .claim(JwtClaims.AMR, listOf(AmrValues.SUOMI_FI))
                    .claim(JwtClaims.GIVEN_NAME, ProfiiliFactory.DEFAULT_GIVEN_NAME)
            if (familyName != null) builder.claim(JwtClaims.FAMILY_NAME, familyName)
            val jwt = builder.build()
            val authentication: Authentication = mockk()
            every { authentication.credentials } returns jwt
            every { securityContext.authentication } returns authentication

            val failure = assertFailure { profiiliService.getVerifiedName(securityContext) }

            failure.all {
                hasClass(NameClaimNotFound::class)
                hasMessage("Claim family_name not found from token.")
            }
            verifySequence { securityContext.authentication }
        }
    }
}
