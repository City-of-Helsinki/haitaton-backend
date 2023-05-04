package fi.hel.haitaton.hanke.attachment.common

import assertk.assertThat
import assertk.assertions.isEqualTo
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.attachment.body
import fi.hel.haitaton.hanke.attachment.failResult
import fi.hel.haitaton.hanke.attachment.response
import fi.hel.haitaton.hanke.attachment.successResult
import fi.hel.haitaton.hanke.attachment.testFile
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
@TestPropertySource(locations = ["classpath:application-test.properties"])
class FileScanServiceITest : DatabaseTest() {

    @Autowired private lateinit var service: FileScanService

    private lateinit var mockWebServer: MockWebServer

    private val testFile = testFile()

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start(6789)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `scanFiles when files are ok should return scanned file`() {
        mockWebServer.enqueue(response(body(results = successResult())))

        val result = service.validate(testFile)

        assertThat(result).isEqualTo(testFile)
    }

    @Test
    fun `scanFiles when problematic file present should detect`() {
        mockWebServer.enqueue(response(body(results = successResult().plus(failResult()))))

        val exception = assertThrows<AttachmentUploadException> { service.validate(testFile) }

        assertThat(exception.message)
            .isEqualTo("Attachment upload exception: Infected file detected, see previous logs.")
    }

    @ValueSource(ints = [400, 500])
    @ParameterizedTest
    fun `scanFiles when http error should throw`(status: Int) {
        mockWebServer.enqueue(MockResponse().setResponseCode(status))

        assertThrows<WebClientResponseException> { service.validate(testFile) }
    }

    @Test
    fun `scanFiles when scan fails should throw`() {
        mockWebServer.enqueue(response(body(success = false, results = failResult())))

        assertThrows<FileScanException> { service.validate(testFile) }
    }
}
