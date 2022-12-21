package fi.hel.haitaton.hanke.allu

import java.time.ZonedDateTime
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.body
import reactor.core.publisher.Mono

class CableReportServiceAllu(
    private val webClient: WebClient,
    private val properties: AlluProperties
) : CableReportService {

    private val baseUrl = properties.baseUrl

    private fun login(): String? {
        try {
            return webClient
                .post()
                .uri("$baseUrl/v2/login")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_PLAIN)
                .body(Mono.just(LoginInfo(properties.username, properties.password)))
                .retrieve()
                .bodyToMono(String::class.java)
                // Allu has started to return the login token in quotes. Remove them.
                // This is undocumented, so we should confirm this is intentional.
                .map { it.trim('"') }
                .block()
        } catch (e: Throwable) {
            throw AlluLoginException(e)
        }
    }

    override fun getCurrentStatus(applicationId: Int): ApplicationStatus? {
        // Allu no longer accepts null in the eventsAfter field. Use a date before Allu was launched
        // to get the full history.
        // The API documentation still says null is interpreted as "complete history",
        // so we should confirm this change is intentional.
        return getApplicationStatusHistory(applicationId, ZonedDateTime.now().withYear(2010))
            .block()
            ?.events
            ?.maxWithOrNull(compareByDescending { it.eventTime })
            ?.newStatus
    }

    override fun getApplicationStatusEvents(
        applicationId: Int,
        eventsAfter: ZonedDateTime?
    ): List<ApplicationStatusEvent> {
        return getApplicationStatusHistory(applicationId, eventsAfter).block()?.events
            ?: emptyList()
    }

    private fun getApplicationStatusHistory(
        applicationId: Int,
        eventsAfter: ZonedDateTime?
    ): Mono<ApplicationHistory> {
        val token = login()!!
        val search = ApplicationHistorySearch(listOf(applicationId), eventsAfter)
        return webClient
            .post()
            .uri("$baseUrl/v2/applicationhistory")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .headers { it.setBearerAuth(token) }
            .body(Mono.just(search))
            .retrieve()
            // API returns an array of ApplicationHistory objects (one for each requested
            // applicationId)
            .bodyToFlux(ApplicationHistory::class.java)
            // As this function always requests just one take the first
            .next()
    }

    override fun create(cableReport: CableReportApplication): Int {
        val token = login()!!
        return webClient
            .post()
            .uri("$baseUrl/v2/cablereports")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .headers { it.setBearerAuth(token) }
            .bodyValue(cableReport)
            .retrieve()
            .bodyToMono(Int::class.java)
            .blockOptional()
            .orElseThrow()
    }

    override fun update(applicationId: Int, cableReport: CableReportApplication) {
        val token = login()!!
        webClient
            .put()
            .uri("$baseUrl/v2/cablereports/$applicationId")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .headers { it.setBearerAuth(token) }
            .body(Mono.just(cableReport))
            .retrieve()
            .bodyToMono(Int::class.java)
            .blockOptional()
            .orElseThrow()
    }

    override fun addAttachment(applicationId: Int, metadata: AttachmentInfo, file: ByteArray) {
        val token = login()!!

        val builder = MultipartBodyBuilder()
        builder.part("metadata", metadata, MediaType.APPLICATION_JSON).filename("metadata")
        builder.part("file", ByteArrayResource(file), MediaType.APPLICATION_PDF).filename("file")
        val multipartData = builder.build()

        webClient
            .post()
            .uri("$baseUrl/v2/applications/$applicationId/attachments")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .accept(MediaType.APPLICATION_JSON)
            .headers { it.setBearerAuth(token) }
            .body(BodyInserters.fromMultipartData(multipartData))
            .exchange()
            .blockOptional()
            .orElseThrow()
    }

    override fun getInformationRequests(applicationId: Int): List<InformationRequest> {
        val token = login()!!
        return webClient
            .get()
            .uri("$baseUrl/v2/applications/$applicationId/informationrequests")
            .accept(MediaType.APPLICATION_JSON)
            .headers { it.setBearerAuth(token) }
            .retrieve()
            .bodyToFlux(InformationRequest::class.java)
            .collectList()
            .blockOptional()
            .orElseThrow()
    }

    override fun respondToInformationRequest(
        applicationId: Int,
        requestId: Int,
        cableReport: CableReportApplication,
        updatedFields: List<InformationRequestFieldKey>
    ) {
        val token = login()!!
        webClient
            .post()
            .uri("$baseUrl/v2/cablereports/$applicationId/informationrequests/$requestId/response")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .headers { it.setBearerAuth(token) }
            .body(Mono.just(CableReportInformationRequestResponse(cableReport, updatedFields)))
            .retrieve()
            .toBodilessEntity()
            .blockOptional()
            .orElseThrow()
    }

    override fun getDecisionPDF(applicationId: Int): ByteArray {
        val token = login()!!
        return webClient
            .get()
            .uri("$baseUrl/v2/cablereports/$applicationId/decision", applicationId)
            .accept(MediaType.APPLICATION_PDF)
            .headers { it.setBearerAuth(token) }
            .exchange()
            .flatMap { it.bodyToMono(ByteArrayResource::class.java) }
            .map { it.byteArray }
            .blockOptional()
            .orElseThrow()
    }

    override fun getDecisionAttachments(applicationId: Int): List<AttachmentInfo> {
        val token = login()!!
        return webClient
            .get()
            .uri("$baseUrl/v2/applications/$applicationId/attachments", applicationId)
            .accept(MediaType.APPLICATION_JSON)
            .headers { it.setBearerAuth(token) }
            .retrieve()
            .bodyToFlux(AttachmentInfo::class.java)
            .collectList()
            .blockOptional()
            .orElseThrow()
    }

    override fun getDecisionAttachmentData(applicationId: Int, attachmentId: Int): ByteArray {
        val token = login()!!
        return webClient
            .get()
            .uri("$baseUrl/v2/applications/$applicationId/attachments/$attachmentId")
            .accept(MediaType.APPLICATION_PDF)
            .headers { it.setBearerAuth(token) }
            .exchange()
            .flatMap { it.bodyToMono(ByteArrayResource::class.java) }
            .map { it.byteArray }
            .blockOptional()
            .orElseThrow()
    }
}

data class AlluProperties(val baseUrl: String, val username: String, val password: String)

data class LoginInfo(val username: String, val password: String)

class AlluException(val errors: List<ErrorInfo>) : RuntimeException()

class AlluLoginException(cause: Throwable) : RuntimeException(cause)

data class ErrorInfo(val errorMessage: String, val additionalInfo: String)

class ErrorFilter : ExchangeFilterFunction {

    override fun filter(request: ClientRequest, next: ExchangeFunction): Mono<ClientResponse> {
        return next.exchange(request).flatMap { resp ->
            if (resp.statusCode() != null && resp.statusCode().isError) {
                resp
                    .bodyToMono(String::class.java)
                    .defaultIfEmpty(resp.statusCode().reasonPhrase)
                    .flatMap { body -> Mono.error(AlluException(listOf(ErrorInfo(body, "")))) }
            } else {
                Mono.just(resp)
            }
        }
    }
}
