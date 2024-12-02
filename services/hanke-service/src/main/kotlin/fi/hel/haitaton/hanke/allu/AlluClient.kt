package fi.hel.haitaton.hanke.allu

import com.auth0.jwt.JWT
import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.attachment.common.ApplicationAttachmentMetadata
import fi.hel.haitaton.hanke.hakemus.HakemusDecisionNotFoundException
import fi.hel.haitaton.hanke.toJsonString
import fi.hel.haitaton.hanke.valmistumisilmoitus.ValmistumisilmoitusType
import java.time.Duration.ofSeconds
import java.time.Instant
import java.time.LocalDate
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
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

private val logger = KotlinLogging.logger {}

const val HAITATON_SYSTEM = "Haitaton-järjestelmä"
const val AUTH_TOKEN_SAFETY_MARGIN_SECONDS = 5L * 60L

class AlluClient(
    private val webClient: WebClient,
    private val properties: AlluProperties,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    var authToken: String? = null
    var authExpiration: Instant? = null

    private val baseUrl = properties.baseUrl
    private val defaultTimeout = ofSeconds(30)
    private val attachmentUploadTimeout = ofSeconds(55)

    fun getToken(): String =
        authToken?.let {
            val expirationCutoff = Instant.now().plusSeconds(AUTH_TOKEN_SAFETY_MARGIN_SECONDS)
            if (authExpiration?.isAfter(expirationCutoff) == true) {
                it
            } else null
        } ?: refreshToken()

    private fun refreshToken(): String {
        authToken = null
        authExpiration = null
        val token = login()
        val decodedJWT = JWT.decode(token)
        authToken = token
        authExpiration = decodedJWT.expiresAtAsInstant
        logger.info { "Renewed Allu login token with expiration date: $authExpiration" }
        return token
    }

    private fun login(): String {
        try {
            val uri = "$baseUrl/v2/login"
            return webClient
                .post()
                .uri(uri)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_PLAIN)
                .bodyValue(LoginInfo(properties.username, properties.password))
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
        return get("applications/$alluApplicationId")
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
        val search = ApplicationHistorySearch(alluApplicationIds, eventsAfter)
        return post("applicationhistory", search)
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

    fun create(application: AlluApplicationData): Int =
        when (application) {
            is AlluCableReportApplicationData -> create(application, "cablereports", "cable report")
            is AlluExcavationNotificationData ->
                create(application, "excavationannouncements", "excavation announcement")
        }

    fun create(cableReport: AlluApplicationData, path: String, name: String): Int {
        logger.info { "Creating $name." }
        return post(path, cableReport)
            .bodyToMono(Int::class.java)
            .timeout(defaultTimeout)
            .doOnError(WebClientResponseException::class.java) {
                logError("Error creating $name to Allu", it)
            }
            .blockOptional()
            .orElseThrow()
    }

    fun update(alluApplicationId: Int, application: AlluApplicationData) {
        when (application) {
            is AlluCableReportApplicationData ->
                update(alluApplicationId, application, "cablereports", "cable report")
            is AlluExcavationNotificationData ->
                update(
                    alluApplicationId,
                    application,
                    "excavationannouncements",
                    "excavation announcement",
                )
        }
    }

    fun update(
        alluApplicationId: Int,
        application: AlluApplicationData,
        path: String,
        name: String,
    ) {
        logger.info { "Updating $name $alluApplicationId." }
        put("$path/$alluApplicationId", application)
            .bodyToMono(Int::class.java)
            .timeout(defaultTimeout)
            .doOnError(WebClientResponseException::class.java) {
                logError("Error updating $name in Allu", it)
            }
            .blockOptional()
            .orElseThrow()
    }

    fun cancel(alluApplicationId: Int) {
        logger.info { "Cancelling application $alluApplicationId." }
        put("applications/$alluApplicationId/cancelled")
            .bodyToMono(Void::class.java)
            .timeout(defaultTimeout)
            .doOnError(WebClientResponseException::class.java) {
                logError("Error canceling application in Allu", it)
            }
            .block()
    }

    /** Send an individual attachment. */
    fun addAttachment(alluApplicationId: Int, attachment: Attachment) {
        postAttachment(alluApplicationId, attachment)
    }

    /** Send many attachments in parallel. */
    fun addAttachments(
        alluApplicationId: Int,
        attachments: List<ApplicationAttachmentMetadata>,
        getContent: (ApplicationAttachmentMetadata) -> ByteArray,
    ) = runBlocking {
        val semaphore = Semaphore(properties.concurrentUploads)
        withContext(ioDispatcher) {
            attachments.forEach {
                launch {
                    semaphore.withPermit {
                        val content = getContent(it)
                        postAttachment(alluApplicationId, it.toAlluAttachment(content))
                    }
                }
            }
        }
    }

    fun reportCompletionDate(
        type: ValmistumisilmoitusType,
        alluApplicationId: Int,
        reportDate: LocalDate,
    ) {
        logger.info { "Reporting ${type.logName} for application $alluApplicationId." }
        put(
                "excavationannouncements/$alluApplicationId/${type.urlSuffix}",
                reportDate.atStartOfDay(TZ_UTC).toJsonString(),
            )
            .bodyToMono(Void::class.java)
            .timeout(defaultTimeout)
            .doOnError(WebClientResponseException::class.java) {
                logError("Error reporting ${type.logName} to Allu", it)
            }
            .block()
    }

    fun getInformationRequest(alluApplicationId: Int): InformationRequest? {
        logger.info { "Fetching information request for application $alluApplicationId." }
        return get("applications/$alluApplicationId/informationrequests")
            .bodyToMono(InformationRequest::class.java)
            .timeout(defaultTimeout)
            .doOnError(WebClientResponseException::class.java) {
                logError("Error getting application information from Allu", it)
            }
            .block()
    }

    fun respondToInformationRequest(
        alluApplicationId: Int,
        requestId: Int,
        applicationData: AlluApplicationData,
        updatedFields: Set<InformationRequestFieldKey>,
    ) {
        logger.info {
            "Responding to information request. Application: $alluApplicationId, request: $requestId. Updated field keys are: ${updatedFields.joinToString()}"
        }

        val path =
            when (applicationData) {
                is AlluCableReportApplicationData ->
                    "cablereports/$alluApplicationId/informationrequests/$requestId/response"

                is AlluExcavationNotificationData ->
                    "excavationannouncements/$alluApplicationId/informationrequests/$requestId/response"
            }

        val request = InformationRequestResponse(applicationData, updatedFields)
        post(path, request).toBodilessEntity().timeout(defaultTimeout).block()
    }

    fun getDecisionPdf(alluApplicationId: Int): ByteArray {
        logger.info { "Fetching decision pdf for application $alluApplicationId." }
        val requestPath = "cablereports/$alluApplicationId/decision"
        return getPdf(alluApplicationId, requestPath)
    }

    fun getOperationalConditionPdf(alluApplicationId: Int): ByteArray {
        logger.info { "Fetching operational condition pdf for application $alluApplicationId." }
        val requestPath = "excavationannouncements/$alluApplicationId/approval/operationalcondition"
        return getPdf(alluApplicationId, requestPath)
    }

    fun getWorkFinishedPdf(alluApplicationId: Int): ByteArray {
        logger.info { "Fetching work finished pdf for application $alluApplicationId." }
        val requestPath = "excavationannouncements/$alluApplicationId/approval/workfinished"
        return getPdf(alluApplicationId, requestPath)
    }

    private fun getPdf(alluApplicationId: Int, path: String): ByteArray {
        val response =
            get(path, MediaType.APPLICATION_PDF)
                .onStatus(
                    { httpStatus -> httpStatus.value() == 404 },
                    {
                        Mono.error(
                            HakemusDecisionNotFoundException(
                                "Document not found in Allu. alluApplicationId=$alluApplicationId"
                            )
                        )
                    },
                )
                .toEntity(ByteArrayResource::class.java)
                .timeout(defaultTimeout)
                .doOnError(WebClientResponseException::class.java) {
                    logError("Error getting PDF from Allu", it)
                }
                .blockOptional()
                .orElseThrow()

        if (response.headers.contentType != MediaType.APPLICATION_PDF) {
            throw AlluApiException(
                path,
                "Decision API didn't return a PDF. RequestContent-Type header: ${response.headers.contentType}",
            )
        }
        val body = response.body ?: throw AlluApiException(path, "Document API returned empty body")
        return body.byteArray
    }

    fun getDecisionAttachments(alluApplicationId: Int): List<AttachmentMetadata> {
        logger.info { "Fetching decision attachments for application $alluApplicationId." }
        return get("applications/$alluApplicationId/attachments")
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
        return get("applications/$alluApplicationId/attachments/$attachmentId")
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
        return post("applications/$alluApplicationId/comments", comment)
            .bodyToMono(Int::class.java)
            .timeout(defaultTimeout)
            .doOnError(WebClientResponseException::class.java) {
                logError("Error adding system comment to Allu", it)
            }
            .blockOptional()
            .orElseThrow()
    }

    private fun postAttachment(alluApplicationId: Int, attachment: Attachment) {
        logger.info { "Sending attachment for application $alluApplicationId." }

        val builder = MultipartBodyBuilder()
        builder
            .part("metadata", attachment.metadata, MediaType.APPLICATION_JSON)
            .filename("metadata")
        builder
            .part(
                "file",
                ByteArrayResource(attachment.file),
                parseMediaType(attachment.metadata.mimeType),
            )
            .filename("file")
        val multipartData = builder.build()

        postRequest(
                "applications/$alluApplicationId/attachments",
                contentType = MediaType.MULTIPART_FORM_DATA,
            )
            .body(BodyInserters.fromMultipartData(multipartData))
            .retrieve()
            .bodyToMono<Unit>()
            .timeout(attachmentUploadTimeout)
            .doOnError(WebClientResponseException::class.java) {
                logError("Error uploading attachment to Allu", it)
            }
            .block()
    }

    private fun get(
        path: String,
        accept: MediaType = MediaType.APPLICATION_JSON,
    ): WebClient.ResponseSpec {
        val token = getToken()
        return webClient
            .get()
            .uri("$baseUrl/v2/$path")
            .accept(accept)
            .headers { it.setBearerAuth(token) }
            .retrieve()
    }

    private fun postRequest(
        path: String,
        contentType: MediaType = MediaType.APPLICATION_JSON,
        accept: MediaType = MediaType.APPLICATION_JSON,
    ): WebClient.RequestBodySpec {
        val token = getToken()
        return webClient
            .post()
            .uri("$baseUrl/v2/$path")
            .contentType(contentType)
            .accept(accept)
            .headers { it.setBearerAuth(token) }
    }

    private fun post(
        path: String,
        body: Any,
        contentType: MediaType = MediaType.APPLICATION_JSON,
        accept: MediaType = MediaType.APPLICATION_JSON,
    ): WebClient.ResponseSpec = postRequest(path, contentType, accept).bodyValue(body).retrieve()

    private fun putRequest(path: String): WebClient.RequestBodySpec {
        val token = getToken()
        return webClient
            .put()
            .uri("$baseUrl/v2/$path")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .headers { it.setBearerAuth(token) }
    }

    private fun put(path: String): WebClient.ResponseSpec = putRequest(path).retrieve()

    private fun put(path: String, body: Any): WebClient.ResponseSpec =
        putRequest(path).bodyValue(body).retrieve()

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
    @Suppress("ArrayInDataClass") val file: ByteArray,
)

class AlluLoginException(cause: Throwable) : RuntimeException(cause)

/** Exception to use when Allu doesn't follow their API descriptions. */
class AlluApiException(requestUri: String, message: String) :
    RuntimeException("$message, request URI: $requestUri")
