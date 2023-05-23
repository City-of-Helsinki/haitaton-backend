package fi.hel.haitaton.hanke.attachment.common

import org.springframework.http.HttpHeaders
import org.springframework.http.HttpHeaders.CONTENT_DISPOSITION
import org.springframework.http.MediaType.parseMediaType

object HeadersBuilder {
    fun buildHeaders(fileName: String, contentType: String): HttpHeaders {
        val headers = HttpHeaders()
        headers.contentType = parseMediaType(contentType)
        headers.add(CONTENT_DISPOSITION, "attachment; filename=${fileName}")
        return headers
    }
}
