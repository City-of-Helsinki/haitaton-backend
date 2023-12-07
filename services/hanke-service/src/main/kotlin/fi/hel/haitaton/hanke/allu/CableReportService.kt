package fi.hel.haitaton.hanke.allu

import fi.hel.haitaton.hanke.application.ApplicationDecisionNotFoundException
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachment
import java.time.Duration.ofSeconds
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.http.MediaType.parseMediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.body
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

private val logger = KotlinLogging.logger {}

const val HAITATON_SYSTEM = "Haitaton järjestelmä"

class CableReportService(
    private val webClient: WebClient,
    private val properties: AlluProperties,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val baseUrl = properties.baseUrl
    private val defaultTimeout = ofSeconds(30)
    private val attachmentUploadTimeout = ofSeconds(55)

    private fun login(): String {
        try {
            val uri = "$baseUrl/v2/login"
            return webClient
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_PLAIN)
                .body(Mono.just(LoginInfo(properties.username, properties.password)))
                .retrieve()
                .bodyToMono(String::class.java)
                .timeout(defaultTimeout)
                .doOnError(WebClientResponseException::class.java) {
                    logError("Error logging in to Allu", it)
                }
                // Allu has gone back and forth on whether it returns the login token surrounded
                // with quotes or not. To be safe, remove them if they are found.
                .map { it.trim('"') }
                .block() ?: throw AlluApiException(uri, "Login token null")
        } catch (e: Throwable) {
            throw AlluLoginException(e)
        }
    }

    fun getApplicationInformation(alluApplicationId: Int): AlluApplicationResponse {
        logger.info { "Fetching application information for application: $alluApplicationId" }
        val token = login()
        return webClient
            .get()
            .uri("$baseUrl/v2/applications/$alluApplicationId")
            .accept(MediaType.APPLICATION_JSON)
            .headers { it.setBearerAuth(token) }
            .retrieve()
            .bodyToMono(AlluApplicationResponse::class.java)
            .timeout(defaultTimeout)
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
    fun getApplicationStatusHistories(
        alluApplicationIds: List<Int>,
        eventsAfter: ZonedDateTime,
    ): List<ApplicationHistory> {
        logger.info { "Fetching application status histories." }
        val token = login()
        val search = ApplicationHistorySearch(alluApplicationIds, eventsAfter)
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
            .timeout(defaultTimeout)
            .doOnError(WebClientResponseException::class.java) {
                logError("Error getting application history from Allu", it)
            }
            .collectList()
            .block()!!
    }

    fun create(cableReport: AlluCableReportApplicationData): Int {
        logger.info { "Creating cable report." }
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
            .timeout(defaultTimeout)
            .doOnError(WebClientResponseException::class.java) {
                logError("Error creating cable report to Allu", it)
            }
            .blockOptional()
            .orElseThrow()
    }

    fun update(alluApplicationId: Int, cableReport: AlluCableReportApplicationData) {
        logger.info { "Updating application $alluApplicationId." }
        val token = login()
        webClient
            .put()
            .uri("$baseUrl/v2/cablereports/$alluApplicationId")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .headers { it.setBearerAuth(token) }
            .body(Mono.just(cableReport))
            .retrieve()
            .bodyToMono(Int::class.java)
            .timeout(defaultTimeout)
            .doOnError(WebClientResponseException::class.java) {
                logError("Error updating cable report in Allu", it)
            }
            .blockOptional()
            .orElseThrow()
    }

    fun cancel(alluApplicationId: Int) {
        logger.info { "Cancelling application $alluApplicationId." }
        val token = login()
        webClient
            .put()
            .uri("$baseUrl/v2/applications/$alluApplicationId/cancelled")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .headers { it.setBearerAuth(token) }
            .retrieve()
            .bodyToMono(Void::class.java)
            .timeout(defaultTimeout)
            .doOnError(WebClientResponseException::class.java) {
                logError("Error canceling application in Allu", it)
            }
            .block()
    }

    /** Send an individual attachment. */
    fun addAttachment(alluApplicationId: Int, attachment: Attachment) {
        val token = login()
        postAttachment(alluApplicationId, token, attachment)
    }

    /** Send many attachments in parallel. */
    fun addAttachments(
        alluApplicationId: Int,
        attachments: List<ApplicationAttachment>,
        getContent: (ApplicationAttachment) -> ByteArray,
    ) = runBlocking {
        val semaphore = Semaphore(properties.concurrentUploads)
        withContext(ioDispatcher) {
            val token = login()
            attachments.forEach {
                launch {
                    semaphore.withPermit {
                        val content = getContent(it)
                        postAttachment(alluApplicationId, token, it.toAlluAttachment(content))
                    }
                }
            }
        }
    }

    fun getInformationRequests(alluApplicationId: Int): List<InformationRequest> {
        logger.info { "Fetching information request for application $alluApplicationId." }
        val token = login()
        return webClient
            .get()
            .uri("$baseUrl/v2/applications/$alluApplicationId/informationrequests")
            .accept(MediaType.APPLICATION_JSON)
            .headers { it.setBearerAuth(token) }
            .retrieve()
            .bodyToFlux(InformationRequest::class.java)
            .timeout(defaultTimeout)
            .doOnError(WebClientResponseException::class.java) {
                logError("Error getting application information from Allu", it)
            }
            .collectList()
            .blockOptional()
            .orElseThrow()
    }

    fun respondToInformationRequest(
        alluApplicationId: Int,
        requestId: Int,
        cableReport: AlluCableReportApplicationData,
        updatedFields: List<InformationRequestFieldKey>,
    ) {
        logger.info { "Responding to information request." }
        val token = login()
        webClient
            .post()
            .uri(
                "$baseUrl/v2/cablereports/$alluApplicationId/informationrequests/$requestId/response"
            )
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .headers { it.setBearerAuth(token) }
            .body(Mono.just(CableReportInformationRequestResponse(cableReport, updatedFields)))
            .retrieve()
            .toBodilessEntity()
            .timeout(defaultTimeout)
            .block()
    }

    fun getDecisionPdf(alluApplicationId: Int): ByteArray {
        logger.info { "Fetching decision pdf for application $alluApplicationId." }
        val token = login()
        val requestUri = "$baseUrl/v2/cablereports/$alluApplicationId/decision"
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
                                "Decision not found in Allu. alluApplicationId=$alluApplicationId"
                            )
                        )
                    }
                )
                .toEntity(ByteArrayResource::class.java)
                .timeout(defaultTimeout)
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

    fun getDecisionAttachments(alluApplicationId: Int): List<AttachmentMetadata> {
        logger.info { "Fetching decision attachments for application $alluApplicationId." }
        val token = login()
        return webClient
            .get()
            .uri("$baseUrl/v2/applications/$alluApplicationId/attachments", alluApplicationId)
            .accept(MediaType.APPLICATION_JSON)
            .headers { it.setBearerAuth(token) }
            .retrieve()
            .bodyToFlux(AttachmentMetadata::class.java)
            .timeout(defaultTimeout)
            .doOnError(WebClientResponseException::class.java) {
                logError("Error getting decision attachments from Allu", it)
            }
            .collectList()
            .blockOptional()
            .orElseThrow()
    }

    fun getDecisionAttachmentData(alluApplicationId: Int, attachmentId: Int): ByteArray {
        logger.info { "Fetching decision attachment for application: $alluApplicationId." }
        val token = login()
        return webClient
            .get()
            .uri("$baseUrl/v2/applications/$alluApplicationId/attachments/$attachmentId")
            .accept(MediaType.APPLICATION_PDF)
            .headers { it.setBearerAuth(token) }
            .retrieve()
            .bodyToMono(ByteArrayResource::class.java)
            .timeout(defaultTimeout)
            .doOnError(WebClientResponseException::class.java) {
                logError("Error getting decision attachment data from Allu", it)
            }
            .map { it.byteArray }
            .blockOptional()
            .orElseThrow()
    }

    /**
     * Send a comment to the application with Haitaton system as the sender.
     *
     * @return The id of the added comment in Allu
     */
    fun sendSystemComment(alluApplicationId: Int, msg: String): Int =
        sendComment(alluApplicationId, Comment(HAITATON_SYSTEM, msg))

    private fun sendComment(alluApplicationId: Int, comment: Comment): Int {
        logger.info { "Sending comment to application: $alluApplicationId." }
        val token = login()
        return webClient
            .post()
            .uri("$baseUrl/v2/applications/$alluApplicationId/comments")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .headers { it.setBearerAuth(token) }
            .body(Mono.just(comment))
            .retrieve()
            .bodyToMono(Int::class.java)
            .timeout(defaultTimeout)
            .doOnError(WebClientResponseException::class.java) {
                logError("Error adding system comment to Allu", it)
            }
            .blockOptional()
            .orElseThrow()
    }

    private fun postAttachment(alluApplicationId: Int, token: String, attachment: Attachment) {
        logger.info { "Sending attachment for application $alluApplicationId." }

        val builder = MultipartBodyBuilder()
        builder
            .part("metadata", attachment.metadata, MediaType.APPLICATION_JSON)
            .filename("metadata")
        builder
            .part(
                "file",
                ByteArrayResource(attachment.file),
                parseMediaType(attachment.metadata.mimeType)
            )
            .filename("file")
        val multipartData = builder.build()

        webClient
            .post()
            .uri("$baseUrl/v2/applications/$alluApplicationId/attachments")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .accept(MediaType.APPLICATION_JSON)
            .headers { it.setBearerAuth(token) }
            .body(BodyInserters.fromMultipartData(multipartData))
            .retrieve()
            .bodyToMono<Unit>()
            .timeout(attachmentUploadTimeout)
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

@ConfigurationProperties(prefix = "haitaton.allu")
data class AlluProperties(
    val baseUrl: String,
    val username: String,
    val password: String,
    val concurrentUploads: Int,
)

data class LoginInfo(val username: String, val password: String)

data class Attachment(
    val metadata: AttachmentMetadata,
    @Suppress("ArrayInDataClass") val file: ByteArray
) {
    constructor(
        contentType: String,
        fileName: String,
        content: ByteArray
    ) : this(
        AttachmentMetadata(id = null, mimeType = contentType, name = fileName, description = null),
        content
    )
}

class AlluException(val errors: List<ErrorInfo>) : RuntimeException()

class AlluLoginException(cause: Throwable) : RuntimeException(cause)

/** Exception to use when Allu doesn't follow their API descriptions. */
class AlluApiException(requestUri: String, message: String) :
    RuntimeException("$message, request URI: $requestUri")

data class ErrorInfo(val errorMessage: String, val additionalInfo: String)
