package fi.hel.haitaton.hanke.application

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import mu.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/hakemukset")
class ApplicationController(
    private val service: ApplicationService,
    private val disclosureLogService: DisclosureLogService
) {
    @GetMapping
    @Operation(
        summary = "Get all applications",
        description =
            "Returns all applications the user can access. If there are none, returns an empty array."
    )
    @ApiResponse(description = "List of application", responseCode = "200")
    fun getAll(): List<Application> {
        val applications = service.getAllApplicationsForUser(currentUserId())
        disclosureLogService.saveDisclosureLogsForApplications(applications, currentUserId())
        return applications
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get one application",
        description = "Returns one application if it exists and the user can access it."
    )
    @ApiResponses(
        value =
            [
                ApiResponse(description = "The requested application", responseCode = "200"),
                ApiResponse(
                    description = "An application was not found with the given id",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    fun getById(@PathVariable(name = "id") id: Long): Application {
        val userId = currentUserId()
        val application = service.getApplicationById(id, userId)
        disclosureLogService.saveDisclosureLogsForApplication(application, userId)
        return application
    }

    @PostMapping
    @Operation(
        summary = "Create a new application",
        description =
            "Returns the created application. The new application is created as a draft, " +
                "i.e. with true in pendingOnClient. The draft is not sent to Allu."
    )
    @ApiResponses(
        value =
            [
                ApiResponse(description = "The created application", responseCode = "200"),
                ApiResponse(
                    description = "The request body was invalid",
                    responseCode = "400",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    fun create(@RequestBody application: Application): Application {
        val userId = currentUserId()
        val createdApplication = service.create(application, userId)
        disclosureLogService.saveDisclosureLogsForApplication(createdApplication, userId)
        return createdApplication
    }

    @PutMapping("/{id}")
    @Operation(
        summary = "Update an application",
        description =
            """Returns the updated application.
               The application can be updated until it has started processing in Allu, i.e. it's still pending.
               If the application hasn't changed since the last update, nothing more is done.
               If the application has been sent to Allu, it will be updated there as well.
               If an Allu-update is required, but it fails, the local version will not be updated.
               The pendingOnClient value can't be changed with this endpoint. Use POST /hakemukset/{id}/send-application for that.
            """
    )
    @ApiResponses(
        value =
            [
                ApiResponse(description = "The updated application", responseCode = "200"),
                ApiResponse(
                    description = "The request body was invalid",
                    responseCode = "400",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
                ApiResponse(
                    description = "An application was not found with the given id",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
                ApiResponse(
                    description =
                        "The application can't be updated because it has started processing in Allu",
                    responseCode = "409",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    fun update(
        @PathVariable(name = "id") id: Long,
        @RequestBody application: Application
    ): Application {
        val userId = currentUserId()
        val updatedApplication =
            service.updateApplicationData(id, application.applicationData, userId)
        disclosureLogService.saveDisclosureLogsForApplication(updatedApplication, currentUserId())
        return updatedApplication
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete an application",
        description =
            """Deletes an application.
               If the application hasn't been sent to Allu, delete it directly.
               If the application is pending in Allu, cancel it in Allu before deleting it locally.
               If the application has proceeded beyond pending in Allu, refuse to delete it.
            """
    )
    @ApiResponses(
        value =
            [
                ApiResponse(description = "Application deleted, no body", responseCode = "200"),
                ApiResponse(
                    description = "An application was not found with the given id",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
                ApiResponse(
                    description =
                        "The application is already processing in Allu and can't be deleted.",
                    responseCode = "409",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    fun delete(@PathVariable(name = "id") id: Long) {
        // TODO: Needs HAI-1345 to check for authorization
        service.delete(id, currentUserId())
    }

    @PostMapping("/{id}/send-application")
    @Operation(
        summary = "Send an application to Allu",
        description =
            """Returns the application with updated status fields.
               Sets the pendingOnClient value of the application to false. This means the application is no longer a draft.
               A clerk at Allu can start processing the application after this call.
               The application can still be updated after this call, up until a clerk has started to process the application.
               Updating should be done with the update endpoint, calling this endpoint multiple times does nothing.
            """
    )
    @ApiResponses(
        value =
            [
                ApiResponse(description = "The sent application", responseCode = "200"),
                ApiResponse(
                    description = "An application was not found with the given id",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
                ApiResponse(
                    description =
                        "The application can't be sent because it has started processing in Allu",
                    responseCode = "409",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    fun sendApplication(@PathVariable(name = "id") id: Long): Application =
        service.sendApplication(id, currentUserId())

    @GetMapping(
        "/{id}/paatos",
        produces = [MediaType.APPLICATION_PDF_VALUE, MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Download a decision",
        description = "Downloads a decision for this application from Allu."
    )
    @ApiResponses(
        value =
            [
                ApiResponse(description = "The decision PDF", responseCode = "200"),
                ApiResponse(
                    description =
                        "An application was not found with the given id or the application didn't have a decision",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    fun downloadDecision(@PathVariable(name = "id") id: Long): ResponseEntity<ByteArray> {
        val (filename, pdfBytes) = service.downloadDecision(id, currentUserId())

        val headers = HttpHeaders()
        headers.add("Content-Disposition", "inline; filename=$filename.pdf")
        return ResponseEntity.ok()
            .headers(headers)
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdfBytes)
    }

    @ExceptionHandler(ApplicationNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @Hidden
    fun missingApplication(ex: ApplicationNotFoundException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2001
    }

    @ExceptionHandler(IncompatibleApplicationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun incompatibleApplicationData(ex: IncompatibleApplicationException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2002
    }

    @ExceptionHandler(ApplicationAlreadyProcessingException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @Hidden
    fun applicationAlreadyProcessing(ex: ApplicationAlreadyProcessingException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2003
    }

    @ExceptionHandler(AlluDataException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @Hidden
    fun alluDataError(ex: AlluDataException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2004
    }

    @ExceptionHandler(ApplicationGeometryException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun applicationGeometryException(ex: ApplicationGeometryException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2005
    }

    @ExceptionHandler(ApplicationDecisionNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @Hidden
    fun applicationDecisionNotFoundException(ex: ApplicationDecisionNotFoundException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2006
    }
}
