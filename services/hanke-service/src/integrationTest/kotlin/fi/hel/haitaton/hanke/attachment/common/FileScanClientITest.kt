package fi.hel.haitaton.hanke.attachment.common

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import fi.hel.haitaton.hanke.IntegrationTest
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
import org.springframework.web.reactive.function.client.WebClientResponseException

class FileScanClientITest : IntegrationTest() {

    @Autowired private lateinit var scanClient: FileScanClient

    private lateinit var mockWebServer: MockWebServer

    private val testFiles = with(testFile()) { listOf(FileScanInput(originalFilename, bytes)) }

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
    fun `scanFiles when files are ok should pass`() {
        mockWebServer.enqueue(response(body(results = successResult())))

        val result = scanClient.scan(testFiles)

        assertThat(result.hasInfected()).isFalse()
    }

    @Test
    fun `scanFiles when problematic file present should detect`() {
        mockWebServer.enqueue(response(body(results = successResult().plus(failResult()))))

        val result = scanClient.scan(testFiles)

        assertThat(result.hasInfected()).isTrue()
    }

    @ValueSource(ints = [400, 500])
    @ParameterizedTest
    fun `scanFiles when http error should throw`(status: Int) {
        mockWebServer.enqueue(MockResponse().setResponseCode(status))

        assertThrows<WebClientResponseException> { scanClient.scan(testFiles) }
    }

    @Test
    fun `scanFiles when scan fails should throw`() {
        mockWebServer.enqueue(response(body(success = false, results = failResult())))

        assertThrows<FileScanException> { scanClient.scan(testFiles) }
    }
}
