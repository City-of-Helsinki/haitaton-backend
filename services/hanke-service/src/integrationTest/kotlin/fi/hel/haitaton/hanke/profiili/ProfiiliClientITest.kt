package fi.hel.haitaton.hanke.profiili

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.cause
import assertk.assertions.hasClass
import assertk.assertions.hasNoCause
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.messageContains
import assertk.assertions.prop
import com.fasterxml.jackson.module.kotlin.readValue
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import fi.hel.haitaton.hanke.factory.ProfiiliFactory
import fi.hel.haitaton.hanke.toJsonString
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence
import java.net.URLEncoder
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.QueueDispatcher
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

class ProfiiliClientITest {

    private lateinit var mockApiTokensApi: MockWebServer
    private lateinit var mockGraphQl: MockWebServer
    private lateinit var mockConfigurationApi: MockWebServer
    private lateinit var profiiliClient: ProfiiliClient
    private val securityContext: SecurityContext = mockk()

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        mockApiTokensApi = MockWebServer()
        mockGraphQl = MockWebServer()
        mockConfigurationApi = MockWebServer()

        val issuer = mockConfigurationApi.url("/auth/realms/helsinki-tunnistus").toString()

        val apiTokensUrl = mockApiTokensApi.url("/api-tokens/").toString()
        mockConfigurationApi.enqueueSuccess("""{"token_endpoint": "$apiTokensUrl"}""")

        val graphQlUrl = mockGraphQl.url("/graphql/").toString()
        val properties = ProfiiliProperties(graphQlUrl, AUDIENCE)
        profiiliClient = ProfiiliClient(properties, WebClient.builder(), issuer)
    }

    @AfterEach
    fun tearDown() {
        mockApiTokensApi.shutdown()
        mockGraphQl.shutdown()
        mockConfigurationApi.shutdown()
        checkUnnecessaryStub()
        confirmVerified(securityContext)
    }

    @Nested
    inner class GetVerifiedName {
        @Test
        fun `throws exception when accessToken not found from authentication`() {
            every { securityContext.authentication } returns null

            val failure = assertFailure { profiiliClient.getVerifiedName(securityContext) }

            failure.all {
                hasClass(VerifiedNameNotFound::class)
                messageContains("User not authenticated")
            }
            assertThat(mockApiTokensApi.requestCount).isEqualTo(0)
            verify { securityContext.authentication }
        }

        @Test
        fun `throws exception when OpenID configuration can't be found`() {
            mockAccessToken()
            mockConfigurationApi.dispatcher = QueueDispatcher()
            mockConfigurationApi.enqueue(
                MockResponse().setResponseCode(404).setBody("This is the message body from error"))

            val failure = assertFailure { profiiliClient.getVerifiedName(securityContext) }

            failure.all {
                hasClass(ProfiiliConfigurationError::class)
                messageContains("Error in Profiili API connection")
                messageContains("Unable to load OpenID configuration")
                messageContains("status=404")
                messageContains("body=This is the message body from error")
                cause().isNotNull().isInstanceOf(WebClientResponseException::class.java)
            }
            assertThat(mockApiTokensApi.requestCount).isEqualTo(0)
            verifySequence { securityContext.authentication }
        }

        @Test
        fun `throws exception when token endpoint can't be found from OpenID configuration`() {
            mockAccessToken()
            mockConfigurationApi.dispatcher = QueueDispatcher()
            mockConfigurationApi.enqueueSuccess("{}")

            val failure = assertFailure { profiiliClient.getVerifiedName(securityContext) }

            failure.all {
                hasClass(ProfiiliConfigurationError::class)
                messageContains("Error in Profiili API connection")
                messageContains("OpenID configuration didn't contain a token endpoint.")
                hasNoCause()
            }
            assertThat(mockApiTokensApi.requestCount).isEqualTo(0)
            verifySequence { securityContext.authentication }
        }

        @Test
        fun `calls configuration API with the correct url and headers`() {
            mockAccessToken()
            mockApiToken()
            mockGraphQl()

            profiiliClient.getVerifiedName(securityContext)

            val request = mockConfigurationApi.takeRequest()
            assertThat(request.requestUrl)
                .isNotNull()
                .prop(HttpUrl::toString)
                .isEqualTo(
                    "http://localhost:${mockConfigurationApi.port}/auth/realms/helsinki-tunnistus/.well-known/openid-configuration")
            assertThat(request.getHeader("Authorization")).isNull()
            assertThat(request.getHeader("Accept")).isEqualTo(MediaType.APPLICATION_JSON_VALUE)
            verifySequence { securityContext.authentication }
        }

        @Test
        fun `throws exception when API tokens not found`() {
            mockAccessToken()
            mockApiTokensApi.enqueue(MockResponse().setResponseCode(404))

            val failure = assertFailure { profiiliClient.getVerifiedName(securityContext) }

            failure.all {
                hasClass(WebClientResponseException.NotFound::class)
                messageContains("404 Not Found from POST")
            }
            assertThat(mockApiTokensApi.requestCount).isEqualTo(1)
            verifySequence { securityContext.authentication }
        }

        @Test
        fun `throws exception when API tokens is empty`() {
            mockAccessToken()
            mockApiTokensApi.enqueueSuccess("{}")

            val failure = assertFailure { profiiliClient.getVerifiedName(securityContext) }

            failure.all {
                hasClass(VerifiedNameNotFound::class)
                messageContains("Verified name of user could not be obtained")
                messageContains("Token response did not contain an access token")
            }
            assertThat(mockApiTokensApi.requestCount).isEqualTo(1)
            verifySequence { securityContext.authentication }
        }

        @Test
        fun `throws exception when there's no access token in the token API response`() {
            mockAccessToken()
            mockApiTokensApi.enqueueSuccess(mapOf("some-other-audience" to "some-other-token"))

            val failure = assertFailure { profiiliClient.getVerifiedName(securityContext) }

            failure.all {
                hasClass(VerifiedNameNotFound::class)
                messageContains("Verified name of user could not be obtained")
                messageContains("Token response did not contain an access token")
            }
            assertThat(mockApiTokensApi.requestCount).isEqualTo(1)
            verifySequence { securityContext.authentication }
        }

        @Test
        fun `calls the API token endpoint with the correct url and headers`() {
            mockAccessToken()
            mockApiToken()
            mockGraphQl()

            profiiliClient.getVerifiedName(securityContext)

            val request = mockApiTokensApi.takeRequest()
            assertThat(request.requestUrl)
                .isNotNull()
                .prop(HttpUrl::toString)
                .isEqualTo("http://localhost:${mockApiTokensApi.port}/api-tokens/")
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer $ACCESS_TOKEN")
            assertThat(request.getHeader("Accept")).isEqualTo(MediaType.APPLICATION_JSON_VALUE)
            assertThat(request.getHeader("Content-Type"))
                .isEqualTo(MediaType.APPLICATION_FORM_URLENCODED_VALUE + ";charset=UTF-8")
            verifySequence { securityContext.authentication }
        }

        @Test
        fun `calls the API token endpoint with the correct body`() {
            mockAccessToken()
            mockApiToken()
            mockGraphQl()

            profiiliClient.getVerifiedName(securityContext)

            val request = mockApiTokensApi.takeRequest()

            assertThat(request.method).isEqualTo("POST")
            val encodedAudience = URLEncoder.encode(AUDIENCE, "UTF-8")
            val encodedGrantType = URLEncoder.encode(ProfiiliClient.TOKEN_API_GRANT_TYPE, "UTF-8")
            val encodedPermission = URLEncoder.encode(ProfiiliClient.TOKEN_API_PERMISSION, "UTF-8")
            val expectedBody =
                "audience=$encodedAudience&grant_type=$encodedGrantType&permission=$encodedPermission"
            assertThat(request.body.readUtf8()).isEqualTo(expectedBody)
            verifySequence { securityContext.authentication }
        }

        @Test
        fun `throws exception when GraphQL request fails`() {
            mockAccessToken()
            mockApiToken()
            mockGraphQl.enqueue(MockResponse().setResponseCode(404))

            val failure = assertFailure { profiiliClient.getVerifiedName(securityContext) }

            failure.all {
                hasClass(WebClientResponseException.NotFound::class)
                messageContains("404 Not Found from POST")
            }
            assertThat(mockApiTokensApi.requestCount).isEqualTo(1)
            assertThat(mockGraphQl.requestCount).isEqualTo(1)
            verifySequence { securityContext.authentication }
        }

        @Test
        fun `throws exception when user doesn't have a profile`() {
            mockAccessToken()
            mockApiToken()
            mockGraphQl.enqueueSuccess(ProfiiliResponse(ProfiiliData(null)))

            val failure = assertFailure { profiiliClient.getVerifiedName(securityContext) }

            failure.all {
                hasClass(VerifiedNameNotFound::class)
                messageContains("Verified name not found from profile.")
            }
            assertThat(mockApiTokensApi.requestCount).isEqualTo(1)
            assertThat(mockGraphQl.requestCount).isEqualTo(1)
            verifySequence { securityContext.authentication }
        }

        @Test
        fun `returns null when user's profile doesn't have verified information`() {
            mockAccessToken()
            mockApiToken()
            mockGraphQl.enqueueSuccess(ProfiiliResponse(ProfiiliData(MyProfile(null))))

            val failure = assertFailure { profiiliClient.getVerifiedName(securityContext) }

            failure.all {
                hasClass(VerifiedNameNotFound::class)
                messageContains("Verified name not found from profile.")
            }
            assertThat(mockApiTokensApi.requestCount).isEqualTo(1)
            assertThat(mockGraphQl.requestCount).isEqualTo(1)
            verifySequence { securityContext.authentication }
        }

        @Test
        fun `returns name when user's profile has verified information`() {
            mockAccessToken()
            mockApiToken()
            mockGraphQl()
            val response = profiiliClient.getVerifiedName(securityContext)

            assertThat(response).isNotNull().all {
                prop(Names::firstName).isEqualTo(ProfiiliFactory.DEFAULT_FIRST_NAME)
                prop(Names::lastName).isEqualTo(ProfiiliFactory.DEFAULT_LAST_NAME)
                prop(Names::givenName).isEqualTo(ProfiiliFactory.DEFAULT_GIVEN_NAME)
            }
            assertThat(mockApiTokensApi.requestCount).isEqualTo(1)
            assertThat(mockGraphQl.requestCount).isEqualTo(1)
            verifySequence { securityContext.authentication }
        }

        @Test
        fun `calls the GraphQL API with correct url and headers`() {
            mockAccessToken()
            mockApiToken()
            mockGraphQl()

            profiiliClient.getVerifiedName(securityContext)

            val request = mockGraphQl.takeRequest()
            assertThat(request.requestUrl)
                .isNotNull()
                .prop(HttpUrl::toString)
                .isEqualTo("http://localhost:${mockGraphQl.port}/graphql/")
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer $API_TOKEN")
            assertThat(request.getHeader("Accept")).isEqualTo(MediaType.APPLICATION_JSON_VALUE)
            assertThat(request.getHeader("Content-Type"))
                .isEqualTo(MediaType.APPLICATION_JSON_VALUE)
            verifySequence { securityContext.authentication }
        }

        @Test
        fun `calls the GraphQL API with the correct body`() {
            mockAccessToken()
            mockApiToken()
            mockGraphQl()

            profiiliClient.getVerifiedName(securityContext)

            val request = mockGraphQl.takeRequest()
            val body = request.body.readUtf8()
            val query: ProfiiliClient.GraphQlQuery = OBJECT_MAPPER.readValue(body)
            assertThat(query).all {
                prop(ProfiiliClient.GraphQlQuery::variables).isNull()
                prop(ProfiiliClient.GraphQlQuery::operationName).isEqualTo("MyProfileQuery")
                prop(ProfiiliClient.GraphQlQuery::query)
                    .isEqualTo(
                        """|query MyProfileQuery {
                           |  myProfile {
                           |    verifiedPersonalInformation {
                           |      firstName
                           |      lastName
                           |      givenName
                           |    }
                           |  }
                           |}
                           |"""
                            .trimMargin())
            }
            verifySequence { securityContext.authentication }
        }
    }

    private fun MockWebServer.enqueueSuccess(body: String) {
        enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(body))
    }

    private fun MockWebServer.enqueueSuccess(body: Any) {
        enqueueSuccess(body.toJsonString())
    }

    private fun mockApiToken() {
        mockApiTokensApi.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(mapOf("access_token" to API_TOKEN).toJsonString()))
    }

    private fun mockAccessToken() {
        val jwtMock = mockk<Jwt>()
        every { jwtMock.tokenValue } returns ACCESS_TOKEN
        val authenticationMock: Authentication = mockk()
        every { authenticationMock.credentials } returns jwtMock
        every { securityContext.authentication } returns authenticationMock
    }

    private fun mockGraphQl() {
        mockGraphQl.enqueueSuccess(
            ProfiiliResponse(ProfiiliData(MyProfile(ProfiiliFactory.DEFAULT_NAMES))))
    }

    companion object {
        private const val AUDIENCE = "profile-api-test"
        private const val ACCESS_TOKEN = "token"
        private const val API_TOKEN = "Api token, that's not a real JWT in this test."
    }
}
