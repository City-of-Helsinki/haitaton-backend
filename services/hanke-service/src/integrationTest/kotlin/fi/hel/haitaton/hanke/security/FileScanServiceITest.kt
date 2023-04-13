package fi.hel.haitaton.hanke.security

import assertk.assertThat
import assertk.assertions.isDataClassEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.toJsonString
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
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.testcontainers.junit.jupiter.Testcontainers

private const val CONTENT_TYPE = "Content-Type"
private const val FILE_ONE = "file1.pdf"
private const val FILE_TWO = "file2.pdf"
private const val FILE_THREE = "file3.pdf"

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["haitaton.clamav.baseUrl=http://localhost:6789"]
)
@ActiveProfiles("default")
class FileScanServiceITest : DatabaseTest() {

    @Autowired private lateinit var service: FileScanService

    private lateinit var mockWebServer: MockWebServer

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
    fun `scanFiles when files are ok should succeed`() {
        mockWebServer.enqueue(response(body(results = successResults(3))))

        val result =
            service.scanFiles(
                setOf(
                    Pair(FILE_ONE, ByteArray(100)),
                    Pair(FILE_TWO, ByteArray(150)),
                    Pair(FILE_THREE, ByteArray(200))
                )
            )

        assertThat(result.virusDetected).isFalse()
        assertThat(result).isDataClassEqualTo(expectedSuccessResult())
    }

    @Test
    fun `scanFiles when problematic file present should detect`() {
        mockWebServer.enqueue(response(body(results = successResults(1).plus(failResults(1)))))

        val result =
            service.scanFiles(setOf(Pair(FILE_ONE, ByteArray(100)), Pair(FILE_TWO, ByteArray(150))))

        assertThat(result.virusDetected).isTrue()
        assertThat(result).isDataClassEqualTo(expectedFailResult())
    }

    @ValueSource(ints = [400, 500])
    @ParameterizedTest
    fun `scanFiles when http error should throw`(status: Int) {
        mockWebServer.enqueue(MockResponse().setResponseCode(status))

        assertThrows<WebClientResponseException> {
            service.scanFiles(setOf(Pair(FILE_ONE, ByteArray(100))))
        }
    }

    @Test
    fun `scanFiles when scan fails should throw`() {
        mockWebServer.enqueue(response(body(success = false, results = failResults(2))))

        assertThrows<FileScanException> {
            service.scanFiles(setOf(Pair(FILE_ONE, ByteArray(100)), Pair(FILE_TWO, ByteArray(300))))
        }
    }

    private fun response(data: String): MockResponse =
        MockResponse()
            .setBody(data)
            .addHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .setResponseCode(200)

    private fun body(success: Boolean = true, results: List<FileResult>): String =
        FileScanResponse(success, FileScanData(result = results)).toJsonString()

    private fun failResults(amount: Int): List<FileResult> =
        (1..amount).map {
            FileResult(name = "file$it.pdf", isInfected = true, viruses = listOf("virus$it"))
        }

    private fun successResults(amount: Int): List<FileResult> =
        (1..amount).map {
            FileResult(name = "file$it.pdf", isInfected = false, viruses = emptyList())
        }

    private fun expectedSuccessResult(): FileScanResult =
        FileScanResult(
            virusDetected = false,
            FileScanResponse(
                true,
                FileScanData(
                    result =
                        listOf(
                            FileResult(FILE_ONE, isInfected = false, viruses = emptyList()),
                            FileResult(FILE_TWO, isInfected = false, viruses = emptyList()),
                            FileResult(FILE_THREE, isInfected = false, viruses = emptyList()),
                        )
                )
            )
        )
    private fun expectedFailResult(): FileScanResult =
        FileScanResult(
            virusDetected = true,
            FileScanResponse(
                true,
                FileScanData(
                    result =
                        listOf(
                            FileResult(FILE_ONE, isInfected = false, viruses = emptyList()),
                            FileResult(FILE_ONE, isInfected = true, viruses = listOf("virus1")),
                        )
                )
            )
        )
}
