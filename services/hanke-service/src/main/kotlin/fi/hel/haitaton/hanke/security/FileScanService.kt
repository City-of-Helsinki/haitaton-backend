package fi.hel.haitaton.hanke.security

import com.fasterxml.jackson.annotation.JsonProperty
import fi.hel.haitaton.hanke.toJsonString
import java.time.Duration.ofSeconds
import mu.KotlinLogging
import org.apache.commons.lang3.BooleanUtils.isNotFalse
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.MediaType.APPLICATION_PDF
import org.springframework.http.MediaType.MULTIPART_FORM_DATA
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.BodyInserters.fromMultipartData
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException

private val logger = KotlinLogging.logger {}

private const val FORM_KEY = "FILES"

@Service
class FileScanService(
    private val fileScanClient: WebClient,
) {

    fun scanFiles(files: Set<Pair<String, ByteArray>>): FileScanResponse {
        logger.info { "Scanning ${files.size} files." }
        val result = getResults(files).also { response -> response.validateStatus() }
        return result.also { logStatus(it) }
    }

    private fun getResults(files: Set<Pair<String, ByteArray>>): FileScanResponse {
        val data =
            MultipartBodyBuilder()
                .apply {
                    files.forEach { (name, bytes) ->
                        part(FORM_KEY, ByteArrayResource(bytes), APPLICATION_PDF).filename(name)
                    }
                }
                .build()

        return fileScanClient
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
    }

    private fun FileScanResponse.validateStatus() {
        if (!success) {
            throw FileScanException("Scan failed, result: ${this.toJsonString()}")
        }
    }

    private fun logStatus(result: FileScanResponse) {
        if (result.hasInfected()) {
            logger.error { "Infected file detected, scan result: ${this.toJsonString()}" }
        } else {
            logger.info { "Files scanned successfully." }
        }
    }
}

data class FileScanResponse(val success: Boolean, val data: FileScanData) {
    fun hasInfected(): Boolean = data.result.any { isNotFalse(it.isInfected) }
}

data class FileScanData(val result: List<FileResult>)

data class FileResult(
    val name: String,
    @JsonProperty("is_infected") val isInfected: Boolean?,
    val viruses: List<String>
)

class FileScanException(message: String) : RuntimeException(message)
