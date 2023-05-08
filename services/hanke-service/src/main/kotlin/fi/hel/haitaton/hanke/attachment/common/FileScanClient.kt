package fi.hel.haitaton.hanke.attachment.common

import com.fasterxml.jackson.annotation.JsonProperty
import fi.hel.haitaton.hanke.toJsonString
import java.time.Duration.ofSeconds
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.MediaType.MULTIPART_FORM_DATA
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters.fromMultipartData
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

private val logger = KotlinLogging.logger {}

private const val FORM_KEY = "FILES"

@Component
class FileScanClient(
    webClientBuilder: WebClient.Builder,
    @Value("\${haitaton.clamav.baseUrl}") clamAvUrl: String,
) {

    private val fileScanClient: WebClient =
        webClientBuilder.baseUrl(clamAvUrl).build().also {
            logger.info { "Initialized file scan client with base-url: $clamAvUrl" }
        }

    fun scan(files: List<FileScanInput>): List<FileResult> {
        logger.info { "Scanning ${files.size} files." }

        val data =
            MultipartBodyBuilder()
                .apply {
                    files.forEach { (name, bytes) ->
                        part(FORM_KEY, ByteArrayResource(bytes)).filename(name)
                    }
                }
                .build()

        val response =
            fileScanClient
                .post()
                .uri("/api/v1/scan")
                .contentType(MULTIPART_FORM_DATA)
                .accept(APPLICATION_JSON)
                .body(fromMultipartData(data))
                .retrieve()
                .bodyToMono(FileScanResponse::class.java)
                .timeout(ofSeconds(60))
                .doOnError(WebClientResponseException::class.java) {
                    logger.error { "Error uploading file: $it" }
                }
                .blockOptional()
                .orElseThrow()

        return extractResults(response).also { results -> logStatus(results) }
    }

    private fun extractResults(response: FileScanResponse): List<FileResult> {
        if (!response.success) {
            throw FileScanException("Scan failed, result: ${this.toJsonString()}")
        }
        return response.data.result
    }

    private fun logStatus(results: List<FileResult>) {
        if (results.hasInfected()) {
            results.filterInfected().forEach {
                logger.warn { "Infected file detected, scan result: ${it.toJsonString()}" }
            }
        } else {
            logger.info { "Files scanned successfully." }
        }
    }
}

data class FileScanInput(val name: String, val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FileScanInput

        if (name != other.name) return false
        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

data class FileScanResponse(val success: Boolean, val data: FileScanData)

data class FileScanData(val result: List<FileResult>)

data class FileResult(
    val name: String,
    @JsonProperty("is_infected") val isInfected: Boolean?,
    val viruses: List<String>
)

class FileScanException(message: String) : RuntimeException(message)

fun List<FileResult>.hasInfected(): Boolean = any { it.isInfected != false }

fun List<FileResult>.filterInfected(): List<FileResult> = filter { it.isInfected != false }
