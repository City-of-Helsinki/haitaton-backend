package fi.hel.haitaton.hanke.permissions

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.configuration.Feature
import fi.hel.haitaton.hanke.configuration.FeatureFlags
import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.validation.ValidHanke
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import mu.KotlinLogging
import org.springframework.http.HttpStatus
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
@SecurityRequirement(name = "bearerAuth")
class HankeKayttajaController(
    private val hankeService: HankeService,
    private val hankeKayttajaService: HankeKayttajaService,
    private val permissionService: PermissionService,
    private val disclosureLogService: DisclosureLogService,
    private val featureFlags: FeatureFlags,
) {
    @GetMapping("/hankkeet/{hankeTunnus}/whoami")
    @Operation(summary = "Get your own permission for a hanke")
    @ApiResponses(
        value =
            [
                ApiResponse(
                    description = "Your permissions",
                    responseCode = "200",
                    content = [Content(schema = Schema(implementation = WhoamiResponse::class))]
                ),
                ApiResponse(
                    description = "Hanke not found",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    fun whoami(@PathVariable hankeTunnus: String): WhoamiResponse {
        val userId = currentUserId()
        val hankeId = hankeService.getHankeIdOrThrow(hankeTunnus)

        val permission =
            permissionService.findPermission(hankeId, userId)
                ?: throw HankeNotFoundException(hankeTunnus)

        val hankeKayttaja = hankeKayttajaService.getKayttajaByUserId(hankeId, userId)
        return WhoamiResponse(hankeKayttaja?.id, permission.kayttooikeustasoEntity)
    }

    @GetMapping("/hankkeet/{hankeTunnus}/kayttajat")
    @Operation(
        summary = "Get Hanke users",
        description = "Returns a list of users and their Hanke related information."
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

        val hanke = hankeService.findHankeOrThrow(hankeTunnus)

        permissionService.verifyHankeUserAuthorization(
            userId = userId,
            hanke = hanke,
            permissionCode = PermissionCode.VIEW
        )

        val users = hankeKayttajaService.getKayttajatByHankeId(hanke.id!!)

        disclosureLogService.saveDisclosureLogsForHankeKayttajat(users, userId)

        logger.info {
            "Found ${users.size} kayttajat for hanke(id=${hanke.id}, hankeTunnus=$hankeTunnus)"
        }

        return HankeKayttajaResponse(users)
    }

    @PutMapping("/hankkeet/{hankeTunnus}/kayttajat")
    @Operation(
        summary = "Update permissions of the listed users.",
        description =
            """
For each user, permissions are updated directly to the active permissions
if the user in question has activated his permissions. If they haven't,
permissions are updated to the user tokens, i.e. this call will change
which permissions the user will get when they activate their permissions.

The reply from the list users endpoint can be modified and used here, but
only the ID and kayttooikeustaso fields are read.

Only the IDs that are mentioned are updated, so only updated IDs need to
be listed. This also means that this endpoint can't be used to remove
permissions.

If removing or adding KAIKKI_OIKEUDET kayttooikeustaso, the caller needs to
have those same permissions.
"""
    )
    @ApiResponses(
        value =
            [
                ApiResponse(description = "Success, no response body", responseCode = "204"),
                ApiResponse(
                    description = "The request body was invalid",
                    responseCode = "400",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
                ApiResponse(
                    description = "Not authorized to change admin permissions",
                    responseCode = "403",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
                ApiResponse(
                    description = "Hanke by requested tunnus not found",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
                ApiResponse(
                    description =
                        "User tried editing their own permissions or there would " +
                            "be no users with KAIKKI_OIKEUDET left.",
                    responseCode = "409",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun updatePermissions(
        @ValidHanke @RequestBody permissions: PermissionUpdate,
        @PathVariable hankeTunnus: String
    ) {
        featureFlags.ensureEnabled(Feature.USER_MANAGEMENT)

        val userId = currentUserId()
        val hanke = hankeService.findHankeOrThrow(hankeTunnus)

        permissionService.verifyHankeUserAuthorization(
            userId = userId,
            hanke = hanke,
            permissionCode = PermissionCode.MODIFY_EDIT_PERMISSIONS
        )

        val deleteAdminPermission =
            permissionService.hasPermission(
                hanke.id!!,
                userId,
                PermissionCode.MODIFY_DELETE_PERMISSIONS
            )

        hankeKayttajaService.updatePermissions(
            hanke,
            permissions.kayttajat.associate { it.id to it.kayttooikeustaso },
            deleteAdminPermission,
            userId
        )
    }

    @PostMapping("/kayttajat")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Identify a user",
        description =
            """
Identifies a user who has been invited to a hanke. Finds a token in database
that's the same as the token given in the request. Activates the permission to
the hanke the token was created for.

Removes the token after a successful identification.
"""
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    description = "User identified, permission to hanke given",
                    responseCode = "204",
                ),
                ApiResponse(
                    description = "Token not found or outdated",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
                ApiResponse(
                    description = "Token doesn't have a user associated with it",
                    responseCode = "500",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
                ApiResponse(
                    description = "Permission already exists",
                    responseCode = "409",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    fun identifyUser(@RequestBody tunnistautuminen: Tunnistautuminen) {
        hankeKayttajaService.createPermissionFromToken(currentUserId(), tunnistautuminen.tunniste)
    }

    data class Tunnistautuminen(val tunniste: String)

    @ExceptionHandler(MissingAdminPermissionException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @Hidden
    fun missingAdminPermissionException(ex: MissingAdminPermissionException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI0005
    }

    @ExceptionHandler(ChangingOwnPermissionException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @Hidden
    fun changingOwnPermissionException(ex: ChangingOwnPermissionException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI4002
    }

    @ExceptionHandler(UsersWithoutKayttooikeustasoException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @Hidden
    fun usersWithoutKayttooikeustasoException(
        ex: UsersWithoutKayttooikeustasoException
    ): HankeError {
        logger.error(ex) { ex.message }
        return HankeError.HAI4003
    }

    @ExceptionHandler(NoAdminRemainingException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @Hidden
    fun noAdminRemainingException(ex: NoAdminRemainingException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI4003
    }

    @ExceptionHandler(HankeKayttajatNotFoundException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun hankeKayttajatNotFoundException(ex: HankeKayttajatNotFoundException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI4001
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @Hidden
    fun tunnisteNotFoundException(ex: TunnisteNotFoundException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI4004
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @Hidden
    fun orphanedTunnisteException(ex: OrphanedTunnisteException): HankeError {
        logger.error(ex) { ex.message }
        return HankeError.HAI4001
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.CONFLICT)
    @Hidden
    fun userAlreadyHasPermissionException(ex: UserAlreadyHasPermissionException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI4003
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.CONFLICT)
    @Hidden
    fun permissionAlreadyExistsException(ex: PermissionAlreadyExistsException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI4003
    }
}
