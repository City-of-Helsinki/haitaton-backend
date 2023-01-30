package fi.hel.haitaton.hanke.gdpr

import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.application.ApplicationService
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.profiili.ProfiiliClient
import io.sentry.Sentry
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/gdpr-api")
@Validated
class GdprController(
    private val applicationService: ApplicationService,
    private val hankeService: HankeService,
    private val profiiliClient: ProfiiliClient,
    private val gdprJsonConverter: GdprJsonConverter,
    private val disclosureLogService: DisclosureLogService,
) {

    @Value("\${haitaton.gdpr.disabled}") var gdprDisabled: Boolean = true

    @GetMapping("/{userId}")
    @Operation(
        summary = "Returns all personal information of the user",
        description =
            "Helsinki profiili calls this endpoint when the a user makes a GDPR request for all " +
                "their personal information. We return all personal information we can link up " +
                "with that user id. Information is returned if the person has created or modified " +
                "a hanke or an application and any of the contacts in those matches the user's " +
                "name. The API has a specific format for the response, it's documented in " +
                "https://helsinkisolutionoffice.atlassian.net/wiki/spaces/DD/pages/80969736/GDPR+API."
    )
    @ApiResponses(
        value =
            [
                ApiResponse(description = "GDPR information", responseCode = "200"),
                ApiResponse(
                    description = "Request’s parameters fail validation",
                    responseCode = "400",
                ),
                ApiResponse(
                    description = "Request’s credentials are missing or invalid",
                    responseCode = "401",
                ),
                ApiResponse(
                    description = "Profile’s data cannot be found with the given id",
                    responseCode = "404",
                ),
                ApiResponse(
                    description = "There has been an unexpected error during the call",
                    responseCode = "500",
                    content = [Content(schema = Schema(implementation = GdprErrorResponse::class))]
                ),
            ]
    )
    fun getByUserId(@PathVariable userId: String): CollectionNode {
        if (gdprDisabled) {
            throw NotImplementedError("/gdpr-api/$userId")
        }

        logger.info { "Finding GDPR information for user $userId" }
        val applications = applicationService.getAllApplicationsForUser(userId)
        val hankkeet = hankeService.loadHankkeetByUserId(userId)
        if (hankkeet.isEmpty() && applications.isEmpty()) {
            logger.warn { "No GDPR information found for user $userId" }
            throw UserNotFoundException(userId)
        }

        val userInfo = profiiliClient.getInfo(userId)
        val gdprInfo = gdprJsonConverter.createGdprJson(applications, hankkeet, userInfo)

        if (gdprInfo == null) {
            logger.warn { "No matching information found for GDPR request for user $userId" }
            throw UserNotFoundException(userId)
        }

        disclosureLogService.saveDisclosureLogsForProfiili(userId, gdprInfo)
        logger.info { "Returning GDPR information for user $userId" }
        return gdprInfo
    }

    class UserNotFoundException(val userId: String) :
        RuntimeException("No data not found for user $userId")

    class NotImplementedError(endpointName: String) :
        RuntimeException("$endpointName called, but not yet implemented")

    @ExceptionHandler(UserNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun userNotFound(ex: UserNotFoundException) {
        logger.warn { ex.message }
        Sentry.captureException(ex)
    }

    @ExceptionHandler(NotImplementedError::class)
    @ResponseStatus(HttpStatus.NOT_IMPLEMENTED)
    fun notImplemented(ex: NotImplementedError) {
        logger.warn { ex.message }
        Sentry.captureException(ex)
    }

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun otherExceptions(ex: Exception): GdprErrorResponse {
        logger.error(ex) { "Error while retrieving GDPR info" }
        Sentry.captureException(ex)
        return GdprErrorResponse(
            listOf(GdprError("HAI0002", LocalizedMessage("Tuntematon virhe", "Unknown error")))
        )
    }

    data class GdprErrorResponse(val errors: List<GdprError>)

    data class GdprError(val code: String, val message: LocalizedMessage)

    data class LocalizedMessage(val fi: String, val en: String)
}
