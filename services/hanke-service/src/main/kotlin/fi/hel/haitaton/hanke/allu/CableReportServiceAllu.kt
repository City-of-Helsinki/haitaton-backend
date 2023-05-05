package fi.hel.haitaton.hanke.allu

import fi.hel.haitaton.hanke.application.ApplicationDecisionNotFoundException
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.body
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

private val logger = KotlinLogging.logger {}

class CableReportServiceAllu(
    private val webClient: WebClient,
    private val properties: AlluProperties,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CableReportService {

    private val baseUrl = properties.baseUrl

    private fun login(): String {
        try {
            val token =
                webClient
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

            return token ?: throw AlluLoginException("Login token null")
        } catch (e: Throwable) {
            throw AlluLoginException(e.message)
        }
    }

    override fun getApplicationInformation(applicationId: Int): AlluApplicationResponse {
        val token = login()
        return webClient
            .get()
            .uri("$baseUrl/v2/applications/$applicationId")
            .accept(MediaType.APPLICATION_JSON)
            .headers { it.setBearerAuth(token) }
            .retrieve()
            .bodyToMono(AlluApplicationResponse::class.java)
            .doOnError(WebClientResponseException::class.java) {
                logError("Error getting application information from Allu", it)
            }
            .blockOptional()
            .orElseThrow()
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
        val token = login()
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
        val token = login()
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
        val token = login()
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

    override fun cancel(applicationId: Int) {
        val token = login()
        webClient
            .put()
            .uri("$baseUrl/v2/applications/$applicationId/cancelled")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .headers { it.setBearerAuth(token) }
            .retrieve()
            .bodyToMono(Void::class.java)
            .doOnError(WebClientResponseException::class.java) {
                logError("Error canceling application in Allu", it)
            }
            .block()
    }

    /** Send an individual attachment. */
    override fun addAttachment(applicationId: Int, attachment: Attachment) {
        val token = login()
        postAttachment(applicationId, token, attachment)
    }

    /** Send many attachments in parallel. */
    override fun addAttachments(alluId: Int, attachments: List<Attachment>) = runBlocking {
        withContext(ioDispatcher) {
            val token = login()
            attachments.forEach { launch { postAttachment(alluId, token, it) } }
        }
    }

    override fun getInformationRequests(applicationId: Int): List<InformationRequest> {
        val token = login()
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
        val token = login()
        webClient
            .post()
            .uri("$baseUrl/v2/cablereports/$applicationId/informationrequests/$requestId/response")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .headers { it.setBearerAuth(token) }
            .body(Mono.just(CableReportInformationRequestResponse(cableReport, updatedFields)))
            .retrieve()
            .toBodilessEntity()
            .block()
    }

    override fun getDecisionPdf(applicationId: Int): ByteArray {
        val token = login()
        val requestUri = "$baseUrl/v2/cablereports/$applicationId/decision"
        val response =
            webClient
                .get()
                .uri(requestUri)
                .accept(MediaType.APPLICATION_PDF)
                .headers { it.setBearerAuth(token) }
                .retrieve()
                .onStatus(
                    { httpStatus -> httpStatus.value() == 404 },
                    {
                        Mono.error(
                            ApplicationDecisionNotFoundException(
                                "Decision not found in Allu. alluid=$applicationId"
                            )
                        )
                    }
                )
                .toEntity(ByteArrayResource::class.java)
                .doOnError(WebClientResponseException::class.java) {
                    logError("Error getting decision PDF from Allu", it)
                }
                .blockOptional()
                .orElseThrow()

        if (response.headers.contentType != MediaType.APPLICATION_PDF) {
            throw AlluApiException(
                requestUri,
                "Decision API didn't return a PDF. RequestContent-Type header: ${response.headers.contentType}"
            )
        }
        val body =
            response.body ?: throw AlluApiException(requestUri, "Decision API returned empty body")
        return body.byteArray
    }

    override fun getDecisionAttachments(applicationId: Int): List<AttachmentMetadata> {
        val token = login()
        return webClient
            .get()
            .uri("$baseUrl/v2/applications/$applicationId/attachments", applicationId)
            .accept(MediaType.APPLICATION_JSON)
            .headers { it.setBearerAuth(token) }
            .retrieve()
            .bodyToFlux(AttachmentMetadata::class.java)
            .doOnError(WebClientResponseException::class.java) {
                logError("Error getting decision attachments from Allu", it)
            }
            .collectList()
            .blockOptional()
            .orElseThrow()
    }

    override fun getDecisionAttachmentData(applicationId: Int, attachmentId: Int): ByteArray {
        val token = login()
        return webClient
            .get()
            .uri("$baseUrl/v2/applications/$applicationId/attachments/$attachmentId")
            .accept(MediaType.APPLICATION_PDF)
            .headers { it.setBearerAuth(token) }
            .retrieve()
            .bodyToMono(ByteArrayResource::class.java)
            .doOnError(WebClientResponseException::class.java) {
                logError("Error getting decision attachment data from Allu", it)
            }
            .map { it.byteArray }
            .blockOptional()
            .orElseThrow()
    }

    private fun postAttachment(alluId: Int, token: String, attachment: Attachment) {
        logger.info { "Sending attachment for application $alluId." }

        val builder = MultipartBodyBuilder()
        builder
            .part("metadata", attachment.metadata, MediaType.APPLICATION_JSON)
            .filename("metadata")
        builder
            .part("file", ByteArrayResource(attachment.file), MediaType.APPLICATION_PDF)
            .filename("file")
        val multipartData = builder.build()

        webClient
            .post()
            .uri("$baseUrl/v2/applications/$alluId/attachments")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .accept(MediaType.APPLICATION_JSON)
            .headers { it.setBearerAuth(token) }
            .body(BodyInserters.fromMultipartData(multipartData))
            .retrieve()
            .bodyToMono<Void>()
            .doOnError(WebClientResponseException::class.java) {
                logError("Error uploading attachment to Allu", it)
            }
            .block()
    }

    private fun logError(msg: String, ex: WebClientResponseException) {
        logger.error {
            "$msg with status ${ex.statusCode} and response:\n${ex.responseBodyAsString}"
        }
    }
}

data class AlluProperties(val baseUrl: String, val username: String, val password: String)

data class LoginInfo(val username: String, val password: String)

data class Attachment(
    val metadata: AttachmentMetadata,
    @Suppress("ArrayInDataClass") val file: ByteArray
)

class AlluException(val errors: List<ErrorInfo>) : RuntimeException()

class AlluLoginException(message: String?) : RuntimeException(message)

/** Exception to use when Allu doesn't follow their API descriptions. */
class AlluApiException(requestUri: String, message: String) :
    RuntimeException("$message, request URI: $requestUri")

data class ErrorInfo(val errorMessage: String, val additionalInfo: String)
