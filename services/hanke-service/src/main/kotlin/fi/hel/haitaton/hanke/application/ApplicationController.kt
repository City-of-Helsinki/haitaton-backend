package fi.hel.haitaton.hanke.application

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeErrorDetail
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.userId
import fi.hel.haitaton.hanke.validation.InvalidApplicationDataException
import fi.hel.haitaton.hanke.validation.ValidApplication
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_PDF
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.CurrentSecurityContext
import org.springframework.security.core.context.SecurityContext
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@Validated
@RestController
@SecurityRequirement(name = "bearerAuth")
@ConditionalOnProperty(
    name = ["haitaton.features.user-management"],
    havingValue = "false",
    matchIfMissing = true
)
class ApplicationController(
    private val applicationService: ApplicationService,
    private val hankeService: HankeService,
    private val disclosureLogService: DisclosureLogService,
) {
    @GetMapping("/hankkeet/{hankeTunnus}/hakemukset")
    @Operation(
        summary = "Get hanke applications",
        description = "Returns list of applications belonging to a given hanke."
    )
    @PreAuthorize("@applicationAuthorizer.authorizeHankeTunnus(#hankeTunnus, 'VIEW')")
    fun getHankeHakemukset(@PathVariable hankeTunnus: String): ApplicationsResponse {
        logger.info { "Finding applications for hanke $hankeTunnus" }
        val userId = currentUserId()
        hankeService.getHankeApplications(hankeTunnus).let { hakemukset ->
            if (hakemukset.isNotEmpty()) {
                disclosureLogService.saveDisclosureLogsForApplications(hakemukset, userId)
            }
            return ApplicationsResponse(hakemukset).also {
                logger.info { "Found ${it.applications.size} applications for hanke $hankeTunnus" }
            }
        }
    }

    @GetMapping("/hakemukset")
    @Operation(
        summary = "Get all applications",
        description =
            "Returns all applications the user can access. If there are none, returns an empty array."
    )
    @ApiResponse(description = "List of application", responseCode = "200")
    fun getAll(): List<Application> {
        val applications = applicationService.getAllApplicationsForUser(currentUserId())
        disclosureLogService.saveDisclosureLogsForApplications(applications, currentUserId())
        return applications
    }

    @GetMapping("/hakemukset/{id}")
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
    @PreAuthorize("@applicationAuthorizer.authorizeApplicationId(#id, 'VIEW')")
    fun getById(@PathVariable(name = "id") id: Long): Application {
        val application = applicationService.getApplicationById(id)
        disclosureLogService.saveDisclosureLogsForApplication(application, currentUserId())
        return application
    }

    @PostMapping("/hakemukset")
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
    @PreAuthorize("@applicationAuthorizer.authorizeCreate(#application)")
    fun create(@ValidApplication @RequestBody application: Application): Application {
        val userId = currentUserId()
        val createdApplication = applicationService.create(application, userId)
        disclosureLogService.saveDisclosureLogsForApplication(createdApplication, userId)
        return createdApplication
    }

    @PostMapping("/hakemukset/johtoselvitys")
    @Operation(
        summary = "Generates new hanke from cable report application and saves application to it.",
        description =
            "Returns the created application. The new application is created as a draft, " +
                "i.e. with true in pendingOnClient. The draft is not sent to Allu. "
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
    fun createWithGeneratedHanke(
        @ValidApplication @RequestBody cableReport: CableReportWithoutHanke,
        @Parameter(hidden = true) @CurrentSecurityContext securityContext: SecurityContext,
    ): Application {
        return hankeService.generateHankeWithApplication(cableReport, securityContext).also {
            disclosureLogService.saveDisclosureLogsForApplication(it, securityContext.userId())
        }
    }

    @PutMapping("/hakemukset/{id}")
    @Operation(
        summary = "Update an application",
        description =
            """Returns the updated application.
               The application can be updated until it has been sent to Allu.
               If the application hasn't changed since the last update, nothing more is done.
               The pendingOnClient value can't be changed with this endpoint.
               Use [POST /hakemukset/{id}/send-application](#/application-controller/sendApplication) for that.
            """
    )
    @ApiResponses(
        value =
            [
                ApiResponse(description = "The updated application", responseCode = "200"),
                ApiResponse(
                    description = "Application contains invalid data",
                    responseCode = "400",
                    content = [Content(schema = Schema(implementation = HankeErrorDetail::class))]
                ),
                ApiResponse(
                    description = "An application was not found with the given id",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
                ApiResponse(
                    description =
                        "The application can't be updated because it has been sent to Allu",
                    responseCode = "409",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    @PreAuthorize("@applicationAuthorizer.authorizeApplicationId(#id, 'EDIT_APPLICATIONS')")
    fun update(
        @PathVariable(name = "id") id: Long,
        @ValidApplication @RequestBody application: Application
    ): Application {
        val userId = currentUserId()
        val updatedApplication =
            applicationService.updateApplicationData(id, application.applicationData, userId)
        disclosureLogService.saveDisclosureLogsForApplication(updatedApplication, userId)
        return updatedApplication
    }

    @DeleteMapping("/hakemukset/{id}")
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
                        "The application is already processing in Allu and can't be deleted",
                    responseCode = "409",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    @PreAuthorize("@applicationAuthorizer.authorizeApplicationId(#id, 'EDIT_APPLICATIONS')")
    fun delete(@PathVariable(name = "id") id: Long): ApplicationDeletionResultDto {
        val userId = currentUserId()
        logger.info { "Received request to delete application id=$id, userId=$userId" }
        val result = applicationService.deleteWithOrphanGeneratedHankeRemoval(id, userId)
        return result
    }

    @PostMapping("/hakemukset/{id}/send-application")
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
                    description = "Application contains invalid data",
                    responseCode = "400",
                    content = [Content(schema = Schema(implementation = HankeErrorDetail::class))]
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
            ]
    )
    @PreAuthorize("@applicationAuthorizer.authorizeApplicationId(#id, 'EDIT_APPLICATIONS')")
    fun sendApplication(@PathVariable(name = "id") id: Long): Application =
        applicationService.sendApplication(id, currentUserId())

    @GetMapping("/hakemukset/{id}/paatos")
    @Operation(
        summary = "Download a decision",
        description = "Downloads a decision for this application from Allu."
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    description = "The decision PDF",
                    responseCode = "200",
                    content = [Content(mediaType = APPLICATION_PDF_VALUE)]
                ),
                ApiResponse(
                    description =
                        "An application was not found with the given id or the application didn't have a decision",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    @PreAuthorize("@applicationAuthorizer.authorizeApplicationId(#id, 'VIEW')")
    fun downloadDecision(@PathVariable(name = "id") id: Long): ResponseEntity<ByteArray> {
        val userId = currentUserId()
        val (filename, pdfBytes) = applicationService.downloadDecision(id, userId)
        val application = applicationService.getApplicationById(id)
        disclosureLogService.saveDisclosureLogsForCableReport(application.toMetadata(), userId)

        val headers = HttpHeaders()
        headers.add("Content-Disposition", "inline; filename=$filename.pdf")
        return ResponseEntity.ok().headers(headers).contentType(APPLICATION_PDF).body(pdfBytes)
    }

    @ExceptionHandler(IncompatibleApplicationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun incompatibleApplicationData(ex: IncompatibleApplicationException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2002
    }

    @ExceptionHandler(AlluDataException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @Hidden
    fun alluDataError(ex: AlluDataException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2004
    }

    @ExceptionHandler(ApplicationAlreadySentException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @Hidden
    fun applicationAlreadySentException(ex: ApplicationAlreadySentException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2009
    }

    @ExceptionHandler(ApplicationGeometryException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun applicationGeometryException(ex: ApplicationGeometryException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2005
    }

    @ExceptionHandler(ApplicationGeometryNotInsideHankeException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun applicationGeometryNotInsideHankeException(
        ex: ApplicationGeometryNotInsideHankeException
    ): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2007
    }

    @ExceptionHandler(ApplicationDecisionNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @Hidden
    fun applicationDecisionNotFoundException(ex: ApplicationDecisionNotFoundException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2006
    }

    @ExceptionHandler(InvalidApplicationDataException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun invalidApplicationDataException(ex: InvalidApplicationDataException): HankeErrorDetail {
        logger.warn(ex) { ex.message }
        return HankeErrorDetail(hankeError = HankeError.HAI2008, errorPaths = ex.errorPaths)
    }
}
