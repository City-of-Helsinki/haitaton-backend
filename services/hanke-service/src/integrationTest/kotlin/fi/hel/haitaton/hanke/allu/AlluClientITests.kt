package fi.hel.haitaton.hanke.allu

import assertk.Assert
import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasClass
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.isZero
import assertk.assertions.messageContains
import assertk.assertions.prop
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import fi.hel.haitaton.hanke.attachment.PDF_BYTES
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentType
import fi.hel.haitaton.hanke.configuration.Configuration.Companion.webClientWithLargeBuffer
import fi.hel.haitaton.hanke.factory.AlluFactory
import fi.hel.haitaton.hanke.factory.ApplicationAttachmentFactory
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory
import fi.hel.haitaton.hanke.hakemus.HakemusDecisionNotFoundException
import fi.hel.haitaton.hanke.parseJson
import fi.hel.haitaton.hanke.toJsonString
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import okhttp3.MultipartReader
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.http.HttpHeaders.CONTENT_DISPOSITION
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.MediaType.APPLICATION_PDF
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE
import org.springframework.http.MediaType.IMAGE_PNG
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

class AlluClientITests {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var service: AlluClient

    private lateinit var authToken: String

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()

        val baseUrl = mockWebServer.url("/").toUrl().toString()
        val properties = AlluProperties(baseUrl, "fake_username", "any_password", 2)
        val webClient = webClientWithLargeBuffer(WebClient.builder())
        authToken = createMockToken()
        service = AlluClient(webClient, properties)
        service.authToken = authToken
        service.authExpiration = Instant.now().plusSeconds(3600)
    }

    @Nested
    inner class GetToken {
        @Test
        fun `doesn't renew token when it's not expired`() {
            val token = service.getToken()

            assertThat(mockWebServer.requestCount).isZero()
            assertThat(token).isEqualTo(authToken)
        }

        @Test
        fun `renews token when it's not yet set`() {
            service.authToken = null
            val mockToken = addStubbedLoginResponse()

            val token = service.getToken()

            assertThat(mockWebServer.takeRequest()).isValidLoginRequest()
            assertThat(token).isEqualTo(mockToken)
            assertThat(service.authToken).isEqualTo(mockToken)
            assertThat(service.authExpiration!!.isAfter(Instant.now())).isTrue()
        }

        @Test
        fun `renews token when it's expiration date is null`() {
            service.authExpiration = null
            val mockToken = addStubbedLoginResponse()

            val token = service.getToken()

            assertThat(mockWebServer.takeRequest()).isValidLoginRequest()
            assertThat(token).isEqualTo(mockToken)
            assertThat(service.authToken).isEqualTo(mockToken)
            assertThat(service.authExpiration!!.isAfter(Instant.now())).isTrue()
        }

        @Test
        fun `renews token when it's expired`() {
            service.authExpiration = Instant.now().minusSeconds(1)
            val mockToken = addStubbedLoginResponse()

            val token = service.getToken()

            assertThat(mockWebServer.requestCount).isGreaterThan(0)
            assertThat(mockWebServer.takeRequest()).isValidLoginRequest()
            assertThat(token).isEqualTo(mockToken)
            assertThat(service.authToken).isEqualTo(mockToken)
            assertThat(service.authExpiration!!.isAfter(Instant.now())).isTrue()
        }

        @Test
        fun `renews token when it's about to expire, but has not expired yet`() {
            service.authExpiration = Instant.now().plusSeconds(AUTH_TOKEN_SAFETY_MARGIN_SECONDS - 1)
            val mockToken = addStubbedLoginResponse()

            val token = service.getToken()

            assertThat(mockWebServer.takeRequest()).isValidLoginRequest()
            assertThat(token).isEqualTo(mockToken)
            assertThat(service.authToken).isEqualTo(mockToken)
            assertThat(service.authExpiration!!.isAfter(Instant.now())).isTrue()
        }
    }

    @CsvSource("pdf,application/pdf", "png,image/png")
    @ParameterizedTest(name = "{displayName} ({arguments})")
    fun `addAttachment should upload attachment`(extension: String, contentType: String) {
        val alluId = 123
        val metadata =
            AlluFactory.createAttachmentMetadata(mimeType = contentType, name = "file.$extension")
        val file = "test file content".toByteArray()
        val attachment = Attachment(metadata, file)
        val mockResponse = MockResponse().setResponseCode(200)
        mockWebServer.enqueue(mockResponse)

        service.addAttachment(alluId, attachment)

        val request = mockWebServer.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/v2/applications/$alluId/attachments")
        assertThat(request.multiPartContentTypes())
            .containsExactly(APPLICATION_JSON_VALUE, contentType)
    }

    @Test
    fun `addAttachments should upload attachments successfully`() {
        val alluId = 123
        val attachment = ApplicationAttachmentFactory.create(applicationId = 123456)
        val attachments = listOf(attachment, attachment, attachment)
        val mockResponse = MockResponse().setResponseCode(200)
        repeat(attachments.size) { mockWebServer.enqueue(mockResponse) }

        service.addAttachments(alluId, attachments) { _ -> PDF_BYTES }

        repeat(attachments.size) {
            val request = mockWebServer.takeRequest()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.path).isEqualTo("/v2/applications/$alluId/attachments")
        }
    }

    @Test
    fun `addAttachments uploads correct metadata`() {
        val alluId = 123
        val attachments =
            listOf(
                ApplicationAttachmentFactory.create(
                    attachmentType = ApplicationAttachmentType.LIIKENNEJARJESTELY,
                    contentType = APPLICATION_PDF_VALUE,
                    fileName = "Katusuunnitelma.pdf",
                ),
                ApplicationAttachmentFactory.create(
                    attachmentType = ApplicationAttachmentType.VALTAKIRJA,
                    contentType = MediaType.IMAGE_PNG_VALUE,
                    fileName = "valtakirja.png",
                ),
                ApplicationAttachmentFactory.create(
                    attachmentType = ApplicationAttachmentType.MUU,
                    contentType = MediaType.TEXT_PLAIN_VALUE,
                    fileName = "Muu liite.txt",
                ),
            )
        val mockResponse = MockResponse().setResponseCode(200)
        repeat(attachments.size) { mockWebServer.enqueue(mockResponse) }

        service.addAttachments(alluId, attachments) { _ -> PDF_BYTES }

        repeat(attachments.size) {
            val request = mockWebServer.takeRequest()
            assertThat(request.getMetadataPart()).isNotNull().all {
                given { actual ->
                    val metadata = attachments.find { it.fileName == actual.name }!!
                    prop(AttachmentMetadata::name).isEqualTo(metadata.fileName)
                    prop(AttachmentMetadata::mimeType).isEqualTo(metadata.contentType)
                    prop(AttachmentMetadata::description)
                        .isEqualTo(metadata.attachmentType.toFinnish())
                }
            }
        }
    }

    @Nested
    inner class Create {
        @Test
        fun `calls Allu with correct path when the application is a cable report application`() {
            val stubbedApplicationId = 1337
            val applicationIdResponse =
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                    .setBody(stubbedApplicationId.toString())
            mockWebServer.enqueue(applicationIdResponse)
            val application = AlluFactory.createCableReportApplicationData()

            val actualApplicationId = service.create(application)

            assertThat(actualApplicationId).isEqualTo(stubbedApplicationId)
            val request = mockWebServer.takeRequest()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.path).isEqualTo("/v2/cablereports")
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer $authToken")
        }

        @Test
        fun `calls Allu with correct path when the application is an excavation notification`() {
            val stubbedApplicationId = 1337
            val applicationIdResponse =
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                    .setBody(stubbedApplicationId.toString())
            mockWebServer.enqueue(applicationIdResponse)
            val application = AlluFactory.createExcavationNotificationData()

            val actualApplicationId = service.create(application)

            assertThat(actualApplicationId).isEqualTo(stubbedApplicationId)
            val request = mockWebServer.takeRequest()
            assertThat(request.method).isEqualTo("POST")
            assertThat(request.path).isEqualTo("/v2/excavationannouncements")
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer $authToken")
        }

        @Test
        fun `throws an exception when there's an error in the application data`() {
            val applicationIdResponse =
                MockResponse()
                    .setResponseCode(400)
                    .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                    .setBody(
                        """[{"errorMessage":"Cable report should have one orderer contact","additionalInfo":"customerWithContacts"}]""")
            mockWebServer.enqueue(applicationIdResponse)

            assertThrows<WebClientResponseException.BadRequest> {
                service.create(AlluFactory.createCableReportApplicationData())
            }

            val createRequest = mockWebServer.takeRequest()
            assertThat(createRequest.method).isEqualTo("POST")
            assertThat(createRequest.path).isEqualTo("/v2/cablereports")
            assertThat(createRequest.getHeader("Authorization")).isEqualTo("Bearer $authToken")
        }
    }

    @Nested
    inner class ReportOperationalCondition {
        private val applicationId = 241
        private val date = LocalDate.of(2024, 8, 12)

        @Test
        fun `calls Allu with the correct path and time`() {
            mockWebServer.enqueue(MockResponse().setResponseCode(200))

            service.reportOperationalCondition(applicationId, date)

            val createRequest = mockWebServer.takeRequest()
            assertThat(createRequest.method).isEqualTo("PUT")
            assertThat(createRequest.path)
                .isEqualTo("/v2/excavationannouncements/$applicationId/operationalcondition")
            assertThat(createRequest.body.readUtf8()).isEqualTo("\"2024-08-12T00:00:00Z\"")
        }

        @Test
        fun `throws an exception if there's a problem with the call`() {
            mockWebServer.enqueue(MockResponse().setResponseCode(409))

            val failure = assertFailure { service.reportOperationalCondition(applicationId, date) }

            failure.all {
                hasClass(WebClientResponseException.Conflict::class)
                messageContains("/v2/excavationannouncements/$applicationId/operationalcondition")
                messageContains("409 Conflict from PUT")
            }
        }
    }

    @Nested
    inner class GetDecisionPdf {
        @Test
        fun `returns PDF file as bytes`() {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(CONTENT_TYPE, APPLICATION_PDF_VALUE)
                    .setBody(pdfContent()))

            val response = service.getDecisionPdf(12)

            assertThat(response).isEqualTo(PDF_BYTES)
            val createRequest = mockWebServer.takeRequest()
            assertThat(createRequest.method).isEqualTo("GET")
            assertThat(createRequest.path).isEqualTo("/v2/cablereports/12/decision")
            assertThat(createRequest.getHeader("Authorization")).isEqualTo("Bearer $authToken")
        }

        @Test
        fun `returns big PDF file as bytes`() {
            val content = Buffer()
            repeat(1000000 / PDF_BYTES.size + 1) { content.write(PDF_BYTES) }
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(CONTENT_TYPE, APPLICATION_PDF_VALUE)
                    .setBody(content))

            val response = service.getDecisionPdf(12)

            assertThat(response).isEqualTo(content.readByteArray())
        }

        @Test
        fun `throws ApplicationDecisionNotFoundException on 404`() {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                    .setBody("Not found"))

            val exception =
                assertThrows<HakemusDecisionNotFoundException> { service.getDecisionPdf(12) }

            assertThat(exception).hasMessage("Document not found in Allu. alluApplicationId=12")
        }

        @Test
        fun `throws WebClientResponseException on other error codes`() {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                    .setBody("Other error"))

            assertThrows<WebClientResponseException> { service.getDecisionPdf(12) }
        }

        @Test
        fun `throws AlluApiException if the response does not have PDF Content-Type`() {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(CONTENT_TYPE, IMAGE_PNG)
                    .setBody(pdfContent()))

            assertThrows<AlluApiException> { service.getDecisionPdf(12) }
        }

        @Test
        fun `throws AlluApiException if the response body is empty`() {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(CONTENT_TYPE, APPLICATION_PDF)
                    .setBody(""))

            assertThrows<AlluApiException> { service.getDecisionPdf(12) }
        }
    }

    @Nested
    inner class GetOperationalConditionPdf {
        @Test
        fun `returns PDF file as bytes`() {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(CONTENT_TYPE, APPLICATION_PDF_VALUE)
                    .setBody(pdfContent()))

            val response = service.getOperationalConditionPdf(12)

            assertThat(response).isEqualTo(PDF_BYTES)
            val createRequest = mockWebServer.takeRequest()
            assertThat(createRequest.method).isEqualTo("GET")
            assertThat(createRequest.path)
                .isEqualTo("/v2/excavationannouncements/12/approval/operationalcondition")
            assertThat(createRequest.getHeader("Authorization")).isEqualTo("Bearer $authToken")
        }

        @Test
        fun `returns big PDF file as bytes`() {
            val content = Buffer()
            repeat(1000000 / PDF_BYTES.size + 1) { content.write(PDF_BYTES) }
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(CONTENT_TYPE, APPLICATION_PDF_VALUE)
                    .setBody(content))

            val response = service.getOperationalConditionPdf(12)

            assertThat(response).isEqualTo(content.readByteArray())
        }

        @Test
        fun `throws ApplicationDecisionNotFoundException on 404`() {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                    .setBody("Not found"))

            val exception =
                assertThrows<HakemusDecisionNotFoundException> {
                    service.getOperationalConditionPdf(12)
                }

            assertThat(exception).hasMessage("Document not found in Allu. alluApplicationId=12")
        }

        @Test
        fun `throws WebClientResponseException on other error codes`() {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                    .setBody("Other error"))

            assertThrows<WebClientResponseException> { service.getOperationalConditionPdf(12) }
        }

        @Test
        fun `throws AlluApiException if the response does not have PDF Content-Type`() {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(CONTENT_TYPE, IMAGE_PNG)
                    .setBody(pdfContent()))

            assertThrows<AlluApiException> { service.getOperationalConditionPdf(12) }
        }

        @Test
        fun `throws AlluApiException if the response body is empty`() {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(CONTENT_TYPE, APPLICATION_PDF)
                    .setBody(""))

            assertThrows<AlluApiException> { service.getOperationalConditionPdf(12) }
        }
    }

    @Nested
    inner class GetWorkFinishedPdf {

        @Test
        fun `returns PDF file as bytes`() {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(CONTENT_TYPE, APPLICATION_PDF_VALUE)
                    .setBody(pdfContent()))

            val response = service.getWorkFinishedPdf(12)

            assertThat(response).isEqualTo(PDF_BYTES)
            val createRequest = mockWebServer.takeRequest()
            assertThat(createRequest.method).isEqualTo("GET")
            assertThat(createRequest.path)
                .isEqualTo("/v2/excavationannouncements/12/approval/workfinished")
            assertThat(createRequest.getHeader("Authorization")).isEqualTo("Bearer $authToken")
        }

        @Test
        fun `returns big PDF file as bytes`() {
            val content = Buffer()
            repeat(1000000 / PDF_BYTES.size + 1) { content.write(PDF_BYTES) }
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(CONTENT_TYPE, APPLICATION_PDF_VALUE)
                    .setBody(content))

            val response = service.getOperationalConditionPdf(12)

            assertThat(response).isEqualTo(content.readByteArray())
        }

        @Test
        fun `throws ApplicationDecisionNotFoundException on 404`() {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                    .setBody("Not found"))

            val exception =
                assertThrows<HakemusDecisionNotFoundException> { service.getWorkFinishedPdf(12) }

            assertThat(exception).hasMessage("Document not found in Allu. alluApplicationId=12")
        }

        @Test
        fun `throws WebClientResponseException on other error codes`() {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(500)
                    .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                    .setBody("Other error"))

            assertThrows<WebClientResponseException> { service.getWorkFinishedPdf(12) }
        }

        @Test
        fun `throws AlluApiException if the response does not have PDF Content-Type`() {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(CONTENT_TYPE, IMAGE_PNG)
                    .setBody(pdfContent()))

            assertThrows<AlluApiException> { service.getWorkFinishedPdf(12) }
        }

        @Test
        fun `throws AlluApiException if the response body is empty`() {
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(CONTENT_TYPE, APPLICATION_PDF)
                    .setBody(""))

            assertThrows<AlluApiException> { service.getWorkFinishedPdf(12) }
        }
    }

    @Nested
    inner class GetApplicationStatusHistories {
        @Test
        fun `returns application histories`() {
            val alluids = listOf(12, 13)
            val eventsAfter = ZonedDateTime.parse("2022-10-10T15:25:34.981654Z")
            val histories = listOf(ApplicationHistoryFactory.create(applicationId = 12))
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                    .setBody(histories.toJsonString()))

            val response = service.getApplicationStatusHistories(alluids, eventsAfter)

            assertThat(response).isEqualTo(histories)
            val createRequest = mockWebServer.takeRequest()
            assertThat(createRequest.method).isEqualTo("POST")
            assertThat(createRequest.path).isEqualTo("/v2/applicationhistory")
            assertThat(createRequest.getHeader("Authorization")).isEqualTo("Bearer $authToken")
        }

        @Test
        fun `doesn't panic on empty response`() {
            val alluids = listOf(12, 13)
            val eventsAfter = ZonedDateTime.parse("2022-10-10T15:25:34.981654Z")
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                    .setBody("[]"))

            val response = service.getApplicationStatusHistories(alluids, eventsAfter)

            assertThat(response).isEqualTo(listOf())
        }

        @Test
        fun `throws error on bad status`() {
            val alluids = listOf(12, 13)
            val eventsAfter = ZonedDateTime.parse("2022-10-10T15:25:34.981654Z")
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(404)
                    .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                    .setBody("Error message"))

            assertFailure { service.getApplicationStatusHistories(alluids, eventsAfter) }
                .hasClass(WebClientResponseException.NotFound::class)
        }
    }

    @Nested
    inner class GetApplicationInformation {
        @Test
        fun `calls the Allu endpoint with a correct request`() {
            val alluid = 12
            val body = AlluFactory.createAlluApplicationResponse(id = alluid)
            val mockResponse =
                MockResponse()
                    .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                    .setResponseCode(200)
                    .setBody(body.toJsonString())
            mockWebServer.enqueue(mockResponse)

            service.getApplicationInformation(alluid)

            val request = mockWebServer.takeRequest()
            assertThat(request.method).isEqualTo("GET")
            assertThat(request.path).isEqualTo("/v2/applications/$alluid")
            assertThat(request.getHeader("Authorization")).isEqualTo("Bearer $authToken")
        }

        @Test
        fun `returns application information when Allu responds with 200 OK`() {
            val alluid = 12
            val body = AlluFactory.createAlluApplicationResponse(id = alluid)
            val mockResponse =
                MockResponse()
                    .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                    .setResponseCode(200)
                    .setBody(body.toJsonString())
            mockWebServer.enqueue(mockResponse)

            val response = service.getApplicationInformation(alluid)

            assertThat(response).isEqualTo(body)
        }

        @Test
        fun `throws exception when Allu returns an error`() {
            val alluid = 12
            val mockResponse = MockResponse().setResponseCode(500)
            mockWebServer.enqueue(mockResponse)

            val failure = assertFailure { service.getApplicationInformation(alluid) }

            failure.hasClass(WebClientResponseException.InternalServerError::class)
        }
    }

    fun Assert<RecordedRequest>.isValidLoginRequest() = given { loginRequest ->
        prop(RecordedRequest::method).isEqualTo("POST")
        prop(RecordedRequest::path).isEqualTo("/v2/login")
        prop("Authorization header") { loginRequest.getHeader("Authorization") }.isNull()
    }

    private fun RecordedRequest.multiPartContentTypes(): List<String> {
        val reader = MultipartReader(body, boundary())
        return generateSequence { reader.nextPart()?.headers?.get(CONTENT_TYPE) }.toList()
    }

    private fun RecordedRequest.getMetadataPart(): AttachmentMetadata? {
        val reader = MultipartReader(body, boundary())
        val metadataPart =
            generateSequence { reader.nextPart() }
                .find { it.headers[CONTENT_DISPOSITION]?.contains("name=\"metadata\"") ?: false }
        return metadataPart?.body?.readUtf8()?.parseJson()
    }

    private fun RecordedRequest.boundary(): String =
        headers[CONTENT_TYPE]?.split(";boundary=")?.last()!!

    private fun addStubbedLoginResponse(): String {
        val stubbedBearer = createMockToken()

        val loginResponse =
            MockResponse()
                .setResponseCode(200)
                .setHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .setBody(stubbedBearer)
        mockWebServer.enqueue(loginResponse)
        return stubbedBearer
    }

    private fun pdfContent(): Buffer {
        val buffer = Buffer()
        buffer.write(PDF_BYTES)
        return buffer
    }

    private fun createMockToken(secondsToAdd: Long = 3600) =
        JWT.create()
            .withExpiresAt(Instant.now().plusSeconds(secondsToAdd))
            .sign(Algorithm.HMAC256("secret"))
}
