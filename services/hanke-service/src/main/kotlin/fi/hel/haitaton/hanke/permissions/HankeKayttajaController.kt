package fi.hel.haitaton.hanke.permissions

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import mu.KotlinLogging
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/hankkeet/{hankeTunnus}/kayttajat")
class HankeKayttajaController(
    private val hankeService: HankeService,
    private val hankeKayttajaService: HankeKayttajaService,
    private val permissionService: PermissionService,
    private val disclosureLogService: DisclosureLogService,
) {

    @GetMapping(produces = [APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Get Hanke users",
        description = "Returns a list of users and their Hanke  related information."
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    description = "Users in Hanke",
                    responseCode = "200",
                    content =
                        [Content(schema = Schema(implementation = HankeKayttajaResponse::class))]
                ),
                ApiResponse(
                    description = "Hanke not found",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    fun getHankeKayttajat(@PathVariable hankeTunnus: String): HankeKayttajaResponse {
        logger.info { "Finding kayttajat for hanke $hankeTunnus" }

        val userId = currentUserId()

        val hanke =
            hankeService.findHankeOrThrow(hankeTunnus).also {
                permissionService.verifyHankeUserAuthorization(
                    userId = userId,
                    hanke = it,
                    permissionCode = PermissionCode.VIEW
                )
            }

        val users = hankeKayttajaService.getKayttajatByHankeId(hanke.id!!)

        return HankeKayttajaResponse(users).also {
            disclosureLogService.saveDisclosureLogsForHankeKayttajat(users, userId)
            logger.info {
                "Found ${it.kayttajat.size} kayttajat for hanke(id=${hanke.id}, hankeTunnus=$hankeTunnus)"
            }
        }
    }
}
