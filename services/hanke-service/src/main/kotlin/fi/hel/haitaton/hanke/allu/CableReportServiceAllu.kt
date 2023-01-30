package fi.hel.haitaton.hanke.allu

import java.time.ZonedDateTime
import mu.KotlinLogging
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.body
import reactor.core.publisher.Mono

private val logger = KotlinLogging.logger {}

class CableReportServiceAllu(
    private val webClient: WebClient,
    private val properties: AlluProperties,
) : CableReportService {

    /**
     * Date from before Allu was launched.
     *
     * Useful for getting the complete history of an application while the history API doesn't
     * accept null in the eventsAfter field.
     */
    private val beforeAlluLaunch = ZonedDateTime.parse("2017-01-01T00:00:00Z")

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
                .doOnError(WebClientResponseException::class.java) {
                    logError("Error logging in to Allu", it)
                }
                // Allu has gone back and forth on whether it returns the login token surrounded
                // with quotes or not. To be safe, remove them if they are found.
                .map { it.trim('"') }
                .block()
        } catch (e: Throwable) {
            throw AlluLoginException(e)
        }
    }

    override fun getCurrentStatus(applicationId: Int): ApplicationStatus? {
        // Allu should accept null in the eventsBefore field of this request, but returns an error
        // at the moment. As a workaround use a time from before Allu was launched.
        return getApplicationStatusHistory(applicationId, beforeAlluLaunch)
            .block()
            ?.events
            ?.maxWithOrNull(compareByDescending { it.eventTime })
            ?.newStatus
    }

    override fun getApplicationStatusEvents(
        applicationId: Int,
        eventsAfter: ZonedDateTime,
    ): List<ApplicationStatusEvent> {
        return getApplicationStatusHistory(applicationId, eventsAfter).block()?.events
            ?: emptyList()
    }

    private fun getApplicationStatusHistory(
        applicationId: Int,
        eventsAfter: ZonedDateTime,
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
            .doOnError(WebClientResponseException::class.java) {
                logError("Error getting application history from Allu", it)
            }
            // As this function always requests just one take the first
            .next()
    }

    /**
     * We could send an empty list for application IDs. Allu would then send histories for all
     * application created by the calling client i.e. Haitaton. This would work great in production.
     * In other environments, this would cause an endless stream of false errors, since the Allu
     * test instance is shared for all local, dev and test environments.
     */
    override fun getApplicationStatusHistories(
        applicationIds: List<Int>,
        eventsAfter: ZonedDateTime,
    ): List<ApplicationHistory> {
        val token = login()!!
        val search = ApplicationHistorySearch(applicationIds, eventsAfter)
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
            .doOnError(WebClientResponseException::class.java) {
                logError("Error getting application history from Allu", it)
            }
            .collectList()
            .block()!!
    }

    override fun create(cableReport: AlluCableReportApplicationData): Int {
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
            .doOnError(WebClientResponseException::class.java) {
                logError("Error creating cable report to Allu", it)
            }
            .blockOptional()
            .orElseThrow()
    }

    override fun update(applicationId: Int, cableReport: AlluCableReportApplicationData) {
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
            .doOnError(WebClientResponseException::class.java) {
                logError("Error updating cable report in Allu", it)
            }
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
            .doOnError(WebClientResponseException::class.java) {
                logError("Error uploading attachment to Allu", it)
            }
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
            .doOnError(WebClientResponseException::class.java) {
                logError("Error getting application information from Allu", it)
            }
            .collectList()
            .blockOptional()
            .orElseThrow()
    }

    override fun respondToInformationRequest(
        applicationId: Int,
        requestId: Int,
        cableReport: AlluCableReportApplicationData,
        updatedFields: List<InformationRequestFieldKey>,
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
            .doOnError(WebClientResponseException::class.java) {
                logError("Error getting decision PDF from Allu", it)
            }
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
            .doOnError(WebClientResponseException::class.java) {
                logError("Error getting decision attachments from Allu", it)
            }
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
            .doOnError(WebClientResponseException::class.java) {
                logError("Error getting decision attachment data from Allu", it)
            }
            .flatMap { it.bodyToMono(ByteArrayResource::class.java) }
            .map { it.byteArray }
            .blockOptional()
            .orElseThrow()
    }

    private fun logError(msg: String, ex: WebClientResponseException) {
        logger.error {
            "$msg with status ${ex.statusCode} and response:\n${ex.responseBodyAsString}"
        }
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
            if (resp.statusCode().isError) {
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
