package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.application.ApplicationService
import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/hakemukset")
@Validated
@SecurityRequirement(name = "bearerAuth")
@ConditionalOnProperty(
    name = ["haitaton.features.kortisto"],
    havingValue = "true",
)
class HakemusController(
    private val applicationService: ApplicationService,
    private val hakemusService: HakemusService,
    private val disclosureLogService: DisclosureLogService,
) {
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
    @PreAuthorize("@applicationAuthorizer.authorizeApplicationId(#id, 'VIEW')")
    fun getById(@PathVariable(name = "id") id: Long): HakemusResponse {
        logger.info { "Finding application $id" }
        val application = applicationService.getApplicationById(id)
        disclosureLogService.saveDisclosureLogsForApplication(application, currentUserId())
        return hakemusService.hakemusResponse(id)
    }
}
