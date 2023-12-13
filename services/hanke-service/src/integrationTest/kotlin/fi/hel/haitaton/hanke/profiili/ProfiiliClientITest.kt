package fi.hel.haitaton.hanke.profiili

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.messageContains
import assertk.assertions.prop
import com.fasterxml.jackson.module.kotlin.readValue
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import fi.hel.haitaton.hanke.accessToken
import fi.hel.haitaton.hanke.toJsonString
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.clearMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.oauth2.core.OAuth2AccessToken
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

class ProfiiliClientITest {

    private lateinit var mockApiTokensApi: MockWebServer
    private lateinit var mockGraphQl: MockWebServer
    private lateinit var profiiliClient: ProfiiliClient
    private val securityContext: SecurityContext = mockk()

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        mockApiTokensApi = MockWebServer()
        mockGraphQl = MockWebServer()

        val apiTokensUrl = mockApiTokensApi.url("/api-tokens/").toString()
        val graphQlUrl = mockGraphQl.url("/graphql/").toString()
        val properties = ProfiiliProperties(graphQlUrl, apiTokensUrl, AUDIENCE)
        profiiliClient = ProfiiliClient(properties, WebClient.builder())
    }

    @AfterEach
    fun tearDown() {
        mockApiTokensApi.shutdown()
        mockGraphQl.shutdown()
        checkUnnecessaryStub()
        confirmVerified(securityContext)
    }

    @Nested
    inner class GetVerifiedName {
        @Test
        fun `throws exception when accessToken not found from authentication`() {
            every { securityContext.accessToken() } returns null

            val failure = assertFailure { profiiliClient.getVerifiedName(securityContext) }

            failure.all {
                hasClass(VerifiedNameNotFound::class)
                messageContains("User not authenticated")
            }
            assertThat(mockApiTokensApi.requestCount).isEqualTo(0)
            verify { securityContext.accessToken() }
        }

        @Test
        fun `throws exception when API tokens not found`() {
            mockAccessToken()
            mockApiTokensApi.enqueue(MockResponse().setResponseCode(404))

            val failure = assertFailure { profiiliClient.getVerifiedName(securityContext) }

            failure.all {
                hasClass(WebClientResponseException.NotFound::class)
                messageContains("404 Not Found from GET")
            }
            assertThat(mockApiTokensApi.requestCount).isEqualTo(1)
            verify { securityContext.authentication }
        }

        @Test
        fun `throws exception when API tokens is empty`() {
            mockAccessToken()
            mockApiTokensApi.enqueueSuccess("{}")

            val failure = assertFailure { profiiliClient.getVerifiedName(securityContext) }

            failure.all {
                hasClass(VerifiedNameNotFound::class)
                messageContains("Profiili audience not found from API tokens.")
            }
            assertThat(mockApiTokensApi.requestCount).isEqualTo(1)
            verify { securityContext.authentication }
        }

        @Test
        fun `throws exception when there's no API token for the correct audience`() {
            mockAccessToken()
            mockApiTokensApi.enqueueSuccess(mapOf("some-other-audience" to "some-other-token"))

            val failure = assertFailure { profiiliClient.getVerifiedName(securityContext) }

            failure.all {
                hasClass(VerifiedNameNotFound::class)
                messageContains("Profiili audience not found from API tokens.")
            }
            assertThat(mockApiTokensApi.requestCount).isEqualTo(1)
            verify { securityContext.authentication }
        }

        @Test
        fun `calls the API token endpoint with correct url and headers`() {
            mockAccessToken()
            mockApiTokensApi.enqueueSuccess(mapOf("some-other-audience" to "some-other-token"))

            assertFailure { profiiliClient.getVerifiedName(securityContext) }

            val request = mockApiTokensApi.takeRequest()
            assertThat(request.requestUrl)
                .isNotNull()
                .prop(HttpUrl::toString)
                .isEqualTo("http://localhost:${mockApiTokensApi.port}/api-tokens/")
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer $ACCESS_TOKEN")
            assertThat(request.getHeader("Accept")).isEqualTo(MediaType.APPLICATION_JSON_VALUE)
            verify { securityContext.authentication }
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
            verify { securityContext.authentication }
        }

        @Test
        fun `returns null when user doesn't have a profile`() {
            clearMocks(securityContext)
            mockAccessToken()
            mockApiToken()
            mockGraphQl.enqueueSuccess(ProfiiliResponse(ProfiiliData(null)))

            val response = profiiliClient.getVerifiedName(securityContext)

            assertThat(response).isNull()
            assertThat(mockApiTokensApi.requestCount).isEqualTo(1)
            assertThat(mockGraphQl.requestCount).isEqualTo(1)
            verify { securityContext.authentication }
        }

        @Test
        fun `returns null when user's profile doesn't have verified information`() {
            mockAccessToken()
            mockApiToken()
            mockGraphQl.enqueueSuccess(ProfiiliResponse(ProfiiliData(MyProfile(null))))

            val response = profiiliClient.getVerifiedName(securityContext)

            assertThat(response).isNull()
            assertThat(mockApiTokensApi.requestCount).isEqualTo(1)
            assertThat(mockGraphQl.requestCount).isEqualTo(1)
            verify { securityContext.authentication }
        }

        @Test
        fun `returns name when user's profile has verified information`() {
            mockAccessToken()
            mockApiToken()
            mockGraphQl.enqueueSuccess(
                ProfiiliResponse(ProfiiliData(MyProfile(Names("Antti-Matti", "Alahärmä", "Antti"))))
            )

            val response = profiiliClient.getVerifiedName(securityContext)

            assertThat(response).isNotNull().all {
                prop(Names::firstName).isEqualTo("Antti-Matti")
                prop(Names::lastName).isEqualTo("Alahärmä")
                prop(Names::givenName).isEqualTo("Antti")
            }
            assertThat(mockApiTokensApi.requestCount).isEqualTo(1)
            assertThat(mockGraphQl.requestCount).isEqualTo(1)
            verify { securityContext.authentication }
        }

        @Test
        fun `calls the GraphQL API with correct url and headers`() {
            mockAccessToken()
            mockApiToken()
            mockGraphQl.enqueueSuccess(
                ProfiiliResponse(ProfiiliData(MyProfile(Names("Antti-Matti", "Alahärmä", "Antti"))))
            )

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
            verify { securityContext.authentication }
        }

        @Test
        fun `calls the GraphQL API with the correct body`() {
            mockAccessToken()
            mockApiToken()
            mockGraphQl.enqueueSuccess(
                ProfiiliResponse(ProfiiliData(MyProfile(Names("Antti-Matti", "Alahärmä", "Antti"))))
            )

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
                            .trimMargin()
                    )
            }
            verify { securityContext.authentication }
        }
    }

    private fun MockWebServer.enqueueSuccess(body: String) {
        enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(body)
        )
    }

    private fun MockWebServer.enqueueSuccess(body: Any) {
        enqueueSuccess(body.toJsonString())
    }

    private fun mockApiToken() {
        mockApiTokensApi.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody(mapOf(AUDIENCE to API_TOKEN).toJsonString())
        )
    }

    private fun mockAccessToken() {
        val authenticationMock: Authentication = mockk()
        every { authenticationMock.credentials } returns
            OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                ACCESS_TOKEN,
                Instant.MIN,
                Instant.MAX
            )
        every { securityContext.authentication } returns authenticationMock
    }

    companion object {
        private const val AUDIENCE = "https://api.hel.fi/auth/helsinkiprofile"
        private const val ACCESS_TOKEN = "token"
        private const val API_TOKEN = "Api token, that's not a real JWT in this test."
    }
}
