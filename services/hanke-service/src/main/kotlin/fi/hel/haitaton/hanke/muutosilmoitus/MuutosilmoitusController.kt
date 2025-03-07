package fi.hel.haitaton.hanke.muutosilmoitus

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeErrorDetail
import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.hakemus.HakemusSendRequest
import fi.hel.haitaton.hanke.hakemus.HakemusUpdateRequest
import fi.hel.haitaton.hanke.hakemus.InvalidHakemusDataException
import fi.hel.haitaton.hanke.hakemus.ValidHakemusUpdateRequest
import fi.hel.haitaton.hanke.taydennys.NoChangesException
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import java.util.UUID
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
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
class MuutosilmoitusController(private val muutosilmoitusService: MuutosilmoitusService) {
    @PostMapping("/hakemukset/{id}/muutosilmoitus")
    @Operation(
        summary = "Create a change notification, i.e. a muutosilmoitus.",
        description =
            """
                Returns the created muutosilmoitus. The muutosilmoitus starts
                with a copy of the data the application has.

                The application needs to be in DECISION or OPERATIONAL_CONDITION
                status.

                For the time being, creating a muutosilmoitus is only supported
                for excavation notifications.
            """,
    )
    @ApiResponses(
        value =
            [
                ApiResponse(description = "The created muutosilmoitus", responseCode = "200"),
                ApiResponse(
                    description = "The application was in the wrong status.",
                    responseCode = "409",
                    content = [Content(schema = Schema(implementation = HankeError::class))],
                ),
                ApiResponse(
                    description = "Operation not supported for application type.",
                    responseCode = "400",
                    content = [Content(schema = Schema(implementation = HankeError::class))],
                ),
            ]
    )
    @PreAuthorize("@hakemusAuthorizer.authorizeHakemusId(#id, 'EDIT_APPLICATIONS')")
    fun create(@PathVariable id: Long): MuutosilmoitusResponse {
        val muutosilmoitus = muutosilmoitusService.create(id, currentUserId())
        logger.info { "Created the muutosilmoitus. ${muutosilmoitus.logString()}" }
        return muutosilmoitus.toResponse()
    }

    @PutMapping("/muutosilmoitukset/{id}")
    @Operation(
        summary = "Update a muutosilmoitus",
        description =
            """
               Returns the updated muutosilmoitus.
               The muutosilmoitus can be updated until it has been sent to Allu.
               If the muutosilmoitus hasn't changed since the last update, nothing more is done.
            """,
    )
    @ApiResponses(
        value =
            [
                ApiResponse(description = "The updated muutosilmoitus", responseCode = "200"),
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
                                            value = "{hankeError: 'HAI2008', errorPaths: ['name']}",
                                        ),
                                        ExampleObject(
                                            name = "Incompatible request",
                                            summary = "Incompatible request example",
                                            value = "{hankeError: 'HAI2002'}",
                                        ),
                                    ],
                            )
                        ],
                ),
                ApiResponse(
                    description = "A muutosilmoitus was not found with the given id",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))],
                ),
                ApiResponse(
                    description =
                        "The muutosilmoitus has already been sent to Allu and can not be updated",
                    responseCode = "409",
                    content = [Content(schema = Schema(implementation = HankeError::class))],
                ),
            ]
    )
    @PreAuthorize("@muutosilmoitusAuthorizer.authorize(#id, 'EDIT_APPLICATIONS')")
    fun update(
        @PathVariable id: UUID,
        @ValidHakemusUpdateRequest @RequestBody request: HakemusUpdateRequest,
    ): MuutosilmoitusResponse =
        muutosilmoitusService.update(id, request, currentUserId()).toResponse()

    @DeleteMapping("/muutosilmoitukset/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Delete a muutosilmoitus",
        description =
            "Cancels a muutosilmoitus and deletes all related data. " +
                "A muutosilmoitus can not be canceled after it has been sent to Allu.",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    description = "Muutosilmoitus was deleted. No body.",
                    responseCode = "204",
                ),
                ApiResponse(
                    description = "A muutosilmoitus was not found with the given id",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))],
                ),
                ApiResponse(
                    description =
                        "The muutosilmoitus has already been sent to Allu and can not be updated",
                    responseCode = "409",
                    content = [Content(schema = Schema(implementation = HankeError::class))],
                ),
            ]
    )
    @PreAuthorize("@muutosilmoitusAuthorizer.authorize(#id, 'EDIT_APPLICATIONS')")
    fun delete(@PathVariable id: UUID) {
        logger.info { "Deleting muutosilmoitus with id $id..." }
        muutosilmoitusService.delete(id, currentUserId())
        logger.info { "Deleted muutosilmoitus with id $id." }
    }

    @PostMapping("/muutosilmoitukset/{id}/laheta")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Send the muutosilmoitus to Allu for processing",
        description =
            """
               Sends the muutosilmoitus.
               - Marks the muutosilmoitus as sent by setting the `sent` field.
               - A clerk at Allu can process the change request after this call.
               - The muutosilmoitus can't be edited after it has been sent.

               Request body is optional. Can be used to request a paper copy of
               the decision replacement to be sent to the address provided.
               Null body will remove paper decision information even if it was set
               on the original hakemus.
            """,
    )
    @PreAuthorize("@muutosilmoitusAuthorizer.authorize(#id, 'EDIT_APPLICATIONS')")
    fun send(@PathVariable id: UUID, @RequestBody(required = false) request: HakemusSendRequest?) =
        muutosilmoitusService.send(id, request?.paperDecisionReceiver, currentUserId())

    @ExceptionHandler(InvalidHakemusDataException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun invalidHakemusDataException(ex: InvalidHakemusDataException): HankeErrorDetail {
        logger.warn(ex) { ex.message }
        return HankeErrorDetail(hankeError = HankeError.HAI2008, errorPaths = ex.errorPaths)
    }

    @ExceptionHandler(MuutosilmoitusAlreadySentException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @Hidden
    fun muutosilmoitusAlreadySentException(ex: MuutosilmoitusAlreadySentException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI7002
    }

    @ExceptionHandler(IncompatibleMuutosilmoitusUpdateException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun incompatibleMuutosilmoitusUpdateException(
        ex: IncompatibleMuutosilmoitusUpdateException
    ): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2002
    }

    @ExceptionHandler(MuutosilmoitusNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @Hidden
    fun muutosilmoitusNotFoundException(ex: MuutosilmoitusNotFoundException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI7001
    }

    @ExceptionHandler(NoChangesException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @Hidden
    fun noChangesException(ex: NoChangesException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI7003
    }
}
