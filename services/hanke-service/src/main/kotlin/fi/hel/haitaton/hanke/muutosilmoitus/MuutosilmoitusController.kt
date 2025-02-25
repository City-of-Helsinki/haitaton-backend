package fi.hel.haitaton.hanke.muutosilmoitus

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.currentUserId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import mu.KotlinLogging
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
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
                status and there needs to be an open information request for the
                application.

                For the time being, creating a muutosilmoitus is only supported
                for excavation notifications.
            """,
    )
    @ApiResponses(
        value =
            [
                ApiResponse(description = "The created täydennys", responseCode = "200"),
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
}
