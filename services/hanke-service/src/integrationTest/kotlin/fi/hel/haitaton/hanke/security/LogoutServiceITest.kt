package fi.hel.haitaton.hanke.security

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.security.LogoutTokenValidator.Companion.BACKCHANNEL_LOGOUT_EVENT
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Date
import java.util.UUID
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtValidationException
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder

@SpringBootTest(properties = ["spring.main.allow-bean-definition-overriding=true"])
class LogoutServiceITest(
    @Autowired private val logoutService: LogoutService,
    @Autowired private val userSessionRepository: UserSessionRepository,
) : IntegrationTest() {

    companion object {
        private const val SUB = "haitaton"
        private val SID = UUID.randomUUID().toString()
        private val KEY_PAIR: Pair<RSAPrivateKey, RSAPublicKey> by lazy {
            val keyPair =
                KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair()

            val privateKey = keyPair.private as RSAPrivateKey
            val publicKey = keyPair.public as RSAPublicKey

            Pair(privateKey, publicKey)
        }
    }

    @Test
    fun `deletes matching user session`() {
        userSessionRepository.save(
            UserSessionEntity(subject = SUB, sessionId = SID, createdAt = Instant.now())
        )
        assertThat(userSessionRepository.findAll().single().subject).isEqualTo(SUB)
        val logoutToken = createLogoutToken(sid = SID)

        logoutService.logout(logoutToken)

        assertThat(userSessionRepository.findAll()).isEmpty()
    }

    @Test
    fun `does not throw exception if user session is not found`() {
        userSessionRepository.save(
            UserSessionEntity(subject = SUB, sessionId = SID, createdAt = Instant.now())
        )
        assertThat(userSessionRepository.findAll().single().subject).isEqualTo(SUB)
        val logoutToken = createLogoutToken(sid = UUID.randomUUID().toString())

        logoutService.logout(logoutToken)

        assertThat(userSessionRepository.findAll().single().subject).isEqualTo(SUB)
    }

    @Test
    fun `throws exception if token is not a JWT`() {
        userSessionRepository.save(
            UserSessionEntity(subject = SUB, sessionId = SID, createdAt = Instant.now())
        )
        assertThat(userSessionRepository.findAll().single().subject).isEqualTo(SUB)

        val exception = runCatching { logoutService.logout("invalid-token") }.exceptionOrNull()

        assertThat(exception?.javaClass).isEqualTo(BadJwtException::class.java)
        assertThat(exception?.message)
            .isEqualTo("An error occurred while attempting to decode the Jwt: Malformed token")
    }

    @Test
    fun `throws exception if token is missing a mandatory claim`() {
        userSessionRepository.save(
            UserSessionEntity(subject = SUB, sessionId = SID, createdAt = Instant.now())
        )
        assertThat(userSessionRepository.findAll().single().subject).isEqualTo(SUB)
        val logoutToken = createLogoutToken(issuer = null)

        val exception = runCatching { logoutService.logout(logoutToken) }.exceptionOrNull()

        assertThat(exception?.javaClass).isEqualTo(JwtValidationException::class.java)
        assertThat(exception?.message)
            .isEqualTo(
                "An error occurred while attempting to decode the Jwt: The iss claim is not valid"
            )
    }

    fun createLogoutToken(
        issuer: String? = "https://haitaton.hel.fi",
        sid: String? = UUID.randomUUID().toString(),
    ): String {
        val claims = stubJwtClaimsSet(issuer, sid = sid)

        val signedJWT =
            SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-key").build(), claims)

        signedJWT.sign(RSASSASigner(KEY_PAIR.first))
        return signedJWT.serialize()
    }

    private fun stubJwtClaimsSet(
        iss: String? = "https://haitaton.hel.fi",
        sub: String? = "subject",
        aud: List<String>? = listOf("haitaton-api-dev"),
        iat: Instant? = Instant.now(),
        exp: Instant? = Instant.now().plusSeconds(60),
        jti: String? = "id",
        events: Map<String, Any>? = mapOf(BACKCHANNEL_LOGOUT_EVENT to "{}"),
        sid: String? = "session-id",
        nonce: String? = null,
    ): JWTClaimsSet =
        JWTClaimsSet.Builder()
            .apply {
                iss?.let { issuer(it) }
                sub?.let { subject(it) }
                aud?.let { audience(it) }
                iat?.let { issueTime(Date.from(it)) }
                exp?.let { expirationTime(Date.from(it)) }
                jti?.let { jwtID(it) }
                events?.let { claim("events", it) }
                sid?.let { claim("sid", it) }
                nonce?.let { claim("nonce", it) }
            }
            .build()

    @TestConfiguration
    class TestConfig {

        @Bean("logoutJwtDecoder")
        @Primary
        fun logoutJwtDecoderForTest(tokenValidator: LogoutTokenValidator): JwtDecoder {
            val decoder = NimbusJwtDecoder.withPublicKey(KEY_PAIR.second).build()
            val issuerValidator = JwtValidators.createDefaultWithIssuer("https://haitaton.hel.fi")
            val validator = DelegatingOAuth2TokenValidator(issuerValidator, tokenValidator)
            decoder.setJwtValidator(validator)
            return decoder
        }
    }
}
