package fi.hel.haitaton.hanke.attachment.common

import java.nio.charset.StandardCharsets
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType.parseMediaType

object HeadersBuilder {
    /**
     * Builds HTTP headers for file download responses.
     *
     * Uses Spring's ContentDisposition builder which properly handles RFC 5987 encoding
     * (filename*=UTF-8''encoded-name) for special characters like commas, semicolons, quotes, etc.
     */
    fun buildHeaders(fileName: String, contentType: String): HttpHeaders {
        val headers = HttpHeaders()
        headers.contentType = parseMediaType(contentType)
        headers.contentDisposition =
            ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build()
        return headers
    }
}
