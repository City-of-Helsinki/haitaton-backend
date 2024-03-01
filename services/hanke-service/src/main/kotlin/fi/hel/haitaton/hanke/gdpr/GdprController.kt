package fi.hel.haitaton.hanke.gdpr

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import io.sentry.Sentry
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/gdpr-api")
@Validated
class GdprController(
    private val gdprService: GdprService,
    private val disclosureLogService: DisclosureLogService,
    private val gdprProperties: GdprProperties,
) {

    @GetMapping("/{userId}")
    @Operation(
        summary = "Returns all personal information of the user",
        description =
            "Helsinki profiili calls this endpoint when the a user makes a GDPR request for all " +
                "their personal information. We return all personal information we can link up " +
                "with that user id. Information is returned if the person has created an " +
                "application. We return the information they wrote down as their own info. " +
                "\n\n" +
                "The API has a specific format for the response, it's documented in " +
                "[Confluence](https://helsinkisolutionoffice.atlassian.net/wiki/spaces/DD/pages/80969736/GDPR+API).",
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
                    responseCode = "204",
                ),
                ApiResponse(
                    description = "There has been an unexpected error during the call",
                    responseCode = "500",
                    content = [Content(schema = Schema(implementation = GdprErrorResponse::class))],
                ),
            ],
    )
    fun getByUserId(
        @AuthenticationPrincipal principal: Jwt,
        @PathVariable userId: String,
    ): ResponseEntity<CollectionNode> {
        authenticate(userId, principal, gdprProperties.queryScope)

        val gdprInfo = gdprService.findGdprInfo(userId)

        if (gdprInfo == null) {
            logger.warn { "No GDPR information found for user $userId" }
            return ResponseEntity.noContent().build()
        }

        disclosureLogService.saveDisclosureLogsForProfiili(userId, gdprInfo)
        logger.info { "Returning GDPR information for user $userId" }
        return ResponseEntity.ok().body(gdprInfo)
    }

    @DeleteMapping("/{userId}")
    @Operation(
        summary = "Deletes all personal information of the user",
        description =
            "Helsinki profiili calls this endpoint when a user makes a GDPR request to remove all " +
                "their personal information. The only information we can associate with a user id is " +
                "the information the creator of an application has entered as their own. We can't have " +
                "applications without information about who has made them, so we have to delete the " +
                "applications. If any of the user's applications have progressed to handling or " +
                "beyond, we refuse to delete anything. " +
                "\n\n" +
                "Setting dry_run to true runs all the checks like we were deleting the information, " +
                "but nothing is deleted. " +
                "\n\n" +
                "The API has a specific format for the response, it's documented in " +
                "[Confluence](https://helsinkisolutionoffice.atlassian.net/wiki/spaces/DD/pages/80969736/GDPR+API).",
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @ApiResponses(
        value =
            [
                ApiResponse(
                    description =
                        "The service allows and has done everything needed so that it no longer " +
                            "contains personal data for the identified profile. This response is " +
                            "also given in the case that the service does not contain any data " +
                            "for the profile or is completely unaware of the identified profile.",
                    responseCode = "204"
                ),
                ApiResponse(
                    description = "Request’s parameters fail validation",
                    responseCode = "400",
                ),
                ApiResponse(
                    description = "Request’s credentials are missing or invalid",
                    responseCode = "401",
                ),
                ApiResponse(
                    description =
                        "Service data related to the identified profile can't be removed " +
                            "from the called service because of some legal reason. The reason(s) " +
                            "for failure are detailed in the response.",
                    responseCode = "403",
                    content = [Content(schema = Schema(implementation = GdprErrorResponse::class))],
                ),
                ApiResponse(
                    description = "There has been an unexpected error during the call",
                    responseCode = "500",
                    content = [Content(schema = Schema(implementation = GdprErrorResponse::class))],
                ),
            ],
    )
    fun deleteUserInformation(
        @AuthenticationPrincipal token: Jwt,
        @PathVariable userId: String,
        @RequestParam("dry_run") dryRun: Boolean = false,
    ) {
        authenticate(userId, token, gdprProperties.deleteScope)

        gdprService.canDelete(userId)

        if (dryRun) {
            logger.info { "Not deleting information, GDPR request was a dry run." }
        } else {
            gdprService.deleteInfo(userId)
        }
    }

    private fun authenticate(userId: String, token: Jwt, requiredScope: String) {
        val sub = token.subject
        val scopes: List<String>? = token.getClaimAsStringList(gdprProperties.authorizationField)

        if (sub != userId) {
            throw AuthenticationException("JWT sub was $sub, but user id in URL was $userId")
        }
        if (scopes == null || !scopes.contains(requiredScope)) {
            throw AuthenticationException(
                "JWT scopes were $scopes, which didn't include $requiredScope"
            )
        }
    }

    class AuthenticationException(message: String) : RuntimeException(message)

    @ExceptionHandler(AuthenticationException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @Hidden
    fun authenticationException(ex: AuthenticationException) {
        logger.warn { ex.message }
        Sentry.captureException(ex)
    }

    @ExceptionHandler(DeleteForbiddenException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @Hidden
    fun deleteForbidden(ex: DeleteForbiddenException): GdprErrorResponse {
        logger.info { "User has active applications, refusing GDPR delete request." }
        Sentry.captureException(ex)
        return GdprErrorResponse(ex.errors)
    }

    @ExceptionHandler(Exception::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @Hidden
    fun otherExceptions(ex: Exception): GdprErrorResponse {
        logger.error(ex) { "Error while retrieving GDPR info" }
        Sentry.captureException(ex)
        return GdprErrorResponse(
            listOf(
                GdprError(
                    "HAI0002",
                    LocalizedMessage(
                        "Tapahtui virhe",
                        "Det inträffade ett fel",
                        "An error occurred"
                    )
                )
            ),
        )
    }

    data class GdprErrorResponse(val errors: List<GdprError>)
}

data class DeleteForbiddenException(val errors: List<GdprError>) : RuntimeException() {
    companion object {
        fun fromSentApplications(applications: List<Application>) =
            DeleteForbiddenException(
                applications.map { GdprError.fromSentApplication(it.applicationIdentifier) }
            )
    }
}

data class GdprError(val code: String, val message: LocalizedMessage) {
    companion object {
        fun fromSentApplication(applicationIdentifier: String?) =
            GdprError(
                HankeError.HAI2003.errorCode,
                LocalizedMessage(
                    "Keskeneräinen hakemus tunnuksella $applicationIdentifier. Ota yhteyttä alueidenkaytto@hel.fi hakemuksen poistamiseksi.",
                    "Pågående ansökan med koden $applicationIdentifier. Kontakta alueidenkaytto@hel.fi för att ta bort ansökan.",
                    "An unfinished application with the ID $applicationIdentifier. Please contact alueidenkaytto@hel.fi to remove the application."
                )
            )
    }
}

data class LocalizedMessage(val fi: String, val sv: String, val en: String)
