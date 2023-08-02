package fi.hel.haitaton.hanke.application

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeErrorDetail
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionCode.EDIT_APPLICATIONS
import fi.hel.haitaton.hanke.permissions.PermissionCode.VIEW
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.validation.InvalidApplicationDataException
import fi.hel.haitaton.hanke.validation.ValidApplication
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import mu.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.MediaType.APPLICATION_PDF
import org.springframework.http.MediaType.APPLICATION_PDF_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
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

@Validated
@RestController
@RequestMapping("/hakemukset")
class ApplicationController(
    private val service: ApplicationService,
    private val hankeService: HankeService,
    private val disclosureLogService: DisclosureLogService,
    private val permissionService: PermissionService,
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
        checkHakemusPermission(id, VIEW)
        val userId = currentUserId()
        val application = service.getApplicationById(id)
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
    fun create(@ValidApplication @RequestBody application: Application): Application {
        val userId = currentUserId()
        val hankeTunnus = application.hankeTunnus

        checkPermissionToCreate(hankeTunnus, userId)

        val createdApplication = service.create(application, userId)

        disclosureLogService.saveDisclosureLogsForApplication(createdApplication, userId)
        return createdApplication
    }

    @PostMapping("/johtoselvitys")
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
        @ValidApplication @RequestBody cableReport: CableReportWithoutHanke
    ): Application {
        val userId = currentUserId()
        return hankeService
            .generateHankeWithApplication(cableReport, userId)
            .applications
            .first()
            .also { disclosureLogService.saveDisclosureLogsForApplication(it, userId) }
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
                        "The application can't be updated because it has started processing in Allu",
                    responseCode = "409",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    fun update(
        @PathVariable(name = "id") id: Long,
        @ValidApplication @RequestBody application: Application
    ): Application {
        checkHakemusPermission(id, EDIT_APPLICATIONS)
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
                        "The application is already processing in Allu and can't be deleted",
                    responseCode = "409",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    fun delete(@PathVariable(name = "id") id: Long) {
        checkHakemusPermission(id, EDIT_APPLICATIONS)
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
    fun sendApplication(@PathVariable(name = "id") id: Long): Application {
        checkHakemusPermission(id, EDIT_APPLICATIONS)
        return service.sendApplication(id, currentUserId())
    }

    @GetMapping("/{id}/paatos", produces = [APPLICATION_PDF_VALUE, APPLICATION_JSON_VALUE])
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
        checkHakemusPermission(id, VIEW)
        val (filename, pdfBytes) = service.downloadDecision(id, currentUserId())

        val headers = HttpHeaders()
        headers.add("Content-Disposition", "inline; filename=$filename.pdf")
        return ResponseEntity.ok().headers(headers).contentType(APPLICATION_PDF).body(pdfBytes)
    }

    fun checkHakemusPermission(hakemusId: Long, permissionCode: PermissionCode) {
        val userId = currentUserId()
        val application = service.getApplicationById(hakemusId)
        val hankeId = hankeService.getHankeId(application.hankeTunnus)

        if (hankeId == null || !permissionService.hasPermission(hankeId, userId, permissionCode)) {
            throw ApplicationNotFoundException(hakemusId)
        }
    }

    fun checkPermissionToCreate(hankeTunnus: String, userId: String) {
        val id = hankeService.getHankeId(hankeTunnus)
        if (id == null || !permissionService.hasPermission(id, userId, EDIT_APPLICATIONS))
            throw HankeNotFoundException(hankeTunnus)
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
