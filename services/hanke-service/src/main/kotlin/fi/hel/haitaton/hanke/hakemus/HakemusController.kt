package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeErrorDetail
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.application.ApplicationAlreadySentException
import fi.hel.haitaton.hanke.application.ApplicationDecisionNotFoundException
import fi.hel.haitaton.hanke.application.ApplicationDeletionResultDto
import fi.hel.haitaton.hanke.application.ApplicationGeometryException
import fi.hel.haitaton.hanke.application.ApplicationGeometryNotInsideHankeException
import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.domain.CreateHankeRequest
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.toHankeError
import fi.hel.haitaton.hanke.validation.ValidCreateHankeRequest
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.ConstraintViolationException
import mu.KotlinLogging
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
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

@RestController
@Validated
@SecurityRequirement(name = "bearerAuth")
class HakemusController(
    private val hakemusService: HakemusService,
    private val hankeService: HankeService,
    private val disclosureLogService: DisclosureLogService,
) {
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
    @PreAuthorize("@hakemusAuthorizer.authorizeHakemusId(#id, 'VIEW')")
    fun getById(@PathVariable(name = "id") id: Long): HakemusResponse {
        logger.info { "Finding application $id" }
        val response = hakemusService.hakemusResponse(id)
        disclosureLogService.saveDisclosureLogsForHakemusResponse(response, currentUserId())
        return response
    }

    @GetMapping("/hankkeet/{hankeTunnus}/hakemukset")
    @Operation(
        summary = "Get hanke applications",
        description = "Returns list of applications belonging to the given hanke."
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    description = "Applications of the requested hanke",
                    responseCode = "200"
                ),
                ApiResponse(
                    description = "Hanke was not found with the given id",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    @PreAuthorize("@hakemusAuthorizer.authorizeHankeTunnus(#hankeTunnus, 'VIEW')")
    fun getHankkeenHakemukset(@PathVariable hankeTunnus: String): HankkeenHakemuksetResponse {
        logger.info { "Finding applications for hanke $hankeTunnus" }
        val response = hakemusService.hankkeenHakemuksetResponse(hankeTunnus)
        logger.info { "Found ${response.applications.size} applications for hanke $hankeTunnus" }
        return response
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
    @PreAuthorize("@hakemusAuthorizer.authorizeCreate(#createHakemusRequest)")
    fun create(
        @ValidCreateHakemusRequest @RequestBody createHakemusRequest: CreateHakemusRequest
    ): HakemusResponse {
        val userId = currentUserId()
        val createdHakemus = hakemusService.create(createHakemusRequest, userId)
        return createdHakemus.toResponse()
    }

    @PostMapping("/johtoselvityshakemus")
    @Operation(
        summary =
            "Generates new hanke from a cable report application and saves an application to it.",
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
        @ValidCreateHankeRequest @RequestBody request: CreateHankeRequest,
        @Parameter(hidden = true) @CurrentSecurityContext securityContext: SecurityContext,
    ): HakemusResponse {
        val hakemus = hankeService.generateHankeWithJohtoselvityshakemus(request, securityContext)
        logger.info {
            "Created hanke and hakemus for a stand-alone johtoselvitys. hakemusId=${hakemus.id} " +
                "hankeTunnus=${hakemus.hankeTunnus} hankeId=${hakemus.hankeId}"
        }
        return hakemus.toResponse()
    }

    @PutMapping("/hakemukset/{id}")
    @Operation(
        summary = "Update an application",
        description =
            """Returns the updated application.
               The application can be updated until it has been sent to Allu.
               If the application hasn't changed since the last update, nothing more is done.
               The pendingOnClient value can't be changed with this endpoint.
               Use [POST /hakemukset/{id}/laheta](#/application-controller/sendHakemus) for that.
            """
    )
    @ApiResponses(
        value =
            [
                ApiResponse(description = "The updated application", responseCode = "200"),
                ApiResponse(
                    description = "Request contains invalid data",
                    responseCode = "400",
                    content =
                        [
                            Content(
                                schema =
                                    Schema(oneOf = [HankeErrorDetail::class, HankeError::class]),
                                examples =
                                    [
                                        ExampleObject(
                                            name = "Validation error",
                                            summary = "Validation error example",
                                            value = "{hankeError: 'HAI2008', errorPaths: ['name']}"
                                        ),
                                        ExampleObject(
                                            name = "Incompatible request",
                                            summary = "Incompatible request example",
                                            value = "{hankeError: 'HAI2002'}"
                                        ),
                                    ]
                            )
                        ]
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
    @PreAuthorize("@hakemusAuthorizer.authorizeHakemusId(#id, 'EDIT_APPLICATIONS')")
    fun update(
        @PathVariable(name = "id") id: Long,
        @ValidHakemusUpdateRequest @RequestBody request: HakemusUpdateRequest
    ): HakemusResponse {
        val userId = currentUserId()
        val response = hakemusService.updateHakemus(id, request, userId)
        disclosureLogService.saveDisclosureLogsForHakemusResponse(response, userId)
        return response
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
    @PreAuthorize("@hakemusAuthorizer.authorizeHakemusId(#id, 'EDIT_APPLICATIONS')")
    fun delete(@PathVariable(name = "id") id: Long): ApplicationDeletionResultDto {
        val userId = currentUserId()
        logger.info { "Received request to delete application id=$id, userId=$userId" }
        val result = hakemusService.deleteWithOrphanGeneratedHankeRemoval(id, userId)
        return result
    }

    @PostMapping("/hakemukset/{id}/laheta")
    @Operation(
        summary = "Send an application to Allu for processing",
        description =
            """Returns the application with updated status fields.
               - Sets the pendingOnClient value of the application to false. This means the application is no longer a draft.
               - A clerk at Allu can start processing the application after this call.
               - The application cannot be edited after it has been sent.
               - The caller needs to be a contact on the application for at least one customer. 
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
                    description = "The application has been sent already",
                    responseCode = "409",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    @PreAuthorize("@hakemusAuthorizer.authorizeHakemusId(#id, 'EDIT_APPLICATIONS')")
    fun sendHakemus(@PathVariable(name = "id") id: Long): HakemusResponse =
        hakemusService.sendHakemus(id, currentUserId()).toResponse()

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
                    content = [Content(mediaType = MediaType.APPLICATION_PDF_VALUE)]
                ),
                ApiResponse(
                    description =
                        "An application was not found with the given id or the application didn't have a decision",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    @PreAuthorize("@hakemusAuthorizer.authorizeHakemusId(#id, 'VIEW')")
    fun downloadDecision(@PathVariable(name = "id") id: Long): ResponseEntity<ByteArray> {
        val userId = currentUserId()
        val (filename, pdfBytes) = hakemusService.downloadDecision(id, userId)
        val application = hakemusService.getById(id)
        disclosureLogService.saveDisclosureLogsForCableReport(application.toMetadata(), userId)

        val headers = HttpHeaders()
        headers.add("Content-Disposition", "inline; filename=$filename.pdf")
        return ResponseEntity.ok()
            .headers(headers)
            .contentType(MediaType.APPLICATION_PDF)
            .body(pdfBytes)
    }

    @ExceptionHandler(InvalidHakemusDataException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun invalidHakemusDataException(ex: InvalidHakemusDataException): HankeErrorDetail {
        logger.warn(ex) { ex.message }
        return HankeErrorDetail(hankeError = HankeError.HAI2008, errorPaths = ex.errorPaths)
    }

    @ExceptionHandler(ApplicationAlreadySentException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @Hidden
    fun applicationAlreadySentException(ex: ApplicationAlreadySentException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2009
    }

    @ExceptionHandler(IncompatibleHakemusUpdateRequestException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun incompatibleHakemusUpdateRequestException(
        ex: IncompatibleHakemusUpdateRequestException
    ): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2002
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

    @ExceptionHandler(InvalidHakemusyhteystietoException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun invalidHakemusyhteystietoException(ex: InvalidHakemusyhteystietoException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2010
    }

    @ExceptionHandler(InvalidHakemusyhteyshenkiloException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun invalidHakemusyhteyshenkiloException(ex: InvalidHakemusyhteyshenkiloException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2011
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException::class)
    @Hidden
    fun handleValidationExceptions(ex: ConstraintViolationException): HankeError {
        logger.warn { ex.message }
        return ex.toHankeError(HankeError.HAI1002)
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(UserNotInContactsException::class)
    @Hidden
    fun userNotInContactsException(ex: UserNotInContactsException): HankeError {
        logger.warn { ex.message }
        return HankeError.HAI2012
    }

    @ExceptionHandler(ApplicationDecisionNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @Hidden
    fun applicationDecisionNotFoundException(ex: ApplicationDecisionNotFoundException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2006
    }
}
