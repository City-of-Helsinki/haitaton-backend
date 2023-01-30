package fi.hel.haitaton.hanke.application

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import mu.KotlinLogging
import org.springframework.http.HttpStatus
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
    @ApiResponses(
        value =
            [
                ApiResponse(description = "List of application", responseCode = "200"),
                ApiResponse(
                    description = "Request’s credentials are missing or invalid",
                    responseCode = "401",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
                ApiResponse(
                    description = "There has been an unexpected error during the call",
                    responseCode = "500",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
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
                    description = "Request’s credentials are missing or invalid",
                    responseCode = "401",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
                ApiResponse(
                    description = "An application was not found with the given id",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
                ApiResponse(
                    description = "There has been an unexpected error during the call",
                    responseCode = "500",
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
            "Creates a new application. The new application is created as a draft, " +
                "i.e. with true in pendingOnClient. If the draft was successfully sent to Allu, " +
                "the response includes the Allu ID of the application."
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
                ApiResponse(
                    description = "Request’s credentials are missing or invalid",
                    responseCode = "401",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
                ApiResponse(
                    description = "An application was not found with the given id",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
                ApiResponse(
                    description = "There has been an unexpected error during the call",
                    responseCode = "500",
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
            "Updates an application. The application can be updated until it has started processing on " +
                "Allu, i.e. it's still pending. The updated application will be sent to Allu, but " +
                "it will be updated locally even if that fails. The pendingOnClient value can't be " +
                "changed with this endpoint. Use POST /hakemukset/{id}/send-application for that."
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
                    description = "Request’s credentials are missing or invalid",
                    responseCode = "401",
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
                ApiResponse(
                    description = "There has been an unexpected error during the call",
                    responseCode = "500",
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

    @PostMapping("/{id}/send-application")
    @Operation(
        summary = "Send an application to Allu",
        description =
            "Sends an application to Allu. Sets the pendingOnClient value of the application to false. " +
                "This means the application is no longer a draft. A clerk at Allu can start " +
                "processing the application after this call. The application can still be updated " +
                "after this call, up until a clerk has started to process the application."
    )
    @ApiResponses(
        value =
            [
                ApiResponse(description = "The sent application", responseCode = "200"),
                ApiResponse(
                    description = "Request’s credentials are missing or invalid",
                    responseCode = "401",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
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
                ApiResponse(
                    description = "There has been an unexpected error during the call",
                    responseCode = "500",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    fun sendApplication(@PathVariable(name = "id") id: Long): Application =
        service.sendApplication(id, currentUserId())

    @ExceptionHandler(ApplicationNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun missingApplication(ex: ApplicationNotFoundException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2001
    }

    @ExceptionHandler(IncompatibleApplicationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun incompatibleApplicationData(ex: IncompatibleApplicationException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2002
    }

    @ExceptionHandler(ApplicationAlreadyProcessingException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun applicationAlreadyProcessing(ex: ApplicationAlreadyProcessingException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2003
    }

    @ExceptionHandler(AlluDataException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun alluDataError(ex: AlluDataException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2004
    }

    @ExceptionHandler(ApplicationGeometryException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun applicationGeometryException(ex: ApplicationGeometryException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2005
    }
}
