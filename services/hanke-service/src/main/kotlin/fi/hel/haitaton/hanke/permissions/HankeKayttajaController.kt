package fi.hel.haitaton.hanke.permissions

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.currentUserId
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.profiili.VerifiedNameNotFound
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import java.util.UUID
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.CurrentSecurityContext
import org.springframework.security.core.context.SecurityContext
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
@SecurityRequirement(name = "bearerAuth")
class HankeKayttajaController(
    private val hankeService: HankeService,
    private val hankeKayttajaService: HankeKayttajaService,
    private val hankekayttajaDeleteService: HankekayttajaDeleteService,
    private val permissionService: PermissionService,
    private val disclosureLogService: DisclosureLogService,
) {
    @GetMapping("/my-permissions")
    @Operation(
        summary = "Get your permissions for your own projects",
        description = "Returns a map of current users Hanke identifiers and respective permissions."
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    description = "Permissions grouped by hankeTunnus.",
                    responseCode = "200",
                )
            ]
    )
    fun whoAmIByHanke(): Map<String, WhoamiResponse> {
        val permissions: List<HankePermission> =
            permissionService.permissionsByHanke(userId = currentUserId())

        return permissions.associate { it.hankeTunnus to it.toWhoamiResponse() }
    }

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
    @PreAuthorize("@hankeKayttajaAuthorizer.authorizeHankeTunnus(#hankeTunnus, 'VIEW')")
    fun whoami(@PathVariable hankeTunnus: String): WhoamiResponse {
        val userId = currentUserId()

        val hankeIdentifier = hankeService.findIdentifier(hankeTunnus)!!
        val permission = permissionService.findPermission(hankeIdentifier.id, userId)!!

        val hankeKayttaja = hankeKayttajaService.getKayttajaByUserId(hankeIdentifier.id, userId)
        return WhoamiResponse(hankeKayttaja?.id, permission.kayttooikeustasoEntity)
    }

    @GetMapping("/kayttajat/{kayttajaId}")
    @Operation(
        summary = "Get a Hanke user",
        description = "Returns a single user and their Hanke related information."
    )
    @ApiResponses(
        value =
            [
                ApiResponse(description = "User info", responseCode = "200"),
                ApiResponse(
                    description = "Invalid UUID",
                    responseCode = "400",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
                ApiResponse(
                    description = "Hanke or user not found",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    @PreAuthorize("@hankeKayttajaAuthorizer.authorizeKayttajaId(#kayttajaId, 'VIEW')")
    fun getHankeKayttaja(@PathVariable kayttajaId: UUID): HankeKayttajaDto =
        hankeKayttajaService.getKayttaja(kayttajaId).toDto().also {
            disclosureLogService.saveDisclosureLogsForHankeKayttaja(it, currentUserId())
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
    @PreAuthorize("@hankeKayttajaAuthorizer.authorizeHankeTunnus(#hankeTunnus, 'VIEW')")
    fun getHankeKayttajat(@PathVariable hankeTunnus: String): HankeKayttajaResponse {
        logger.info { "Finding kayttajat for hanke $hankeTunnus" }

        val hankeIdentifier = hankeService.findIdentifier(hankeTunnus)!!

        val users = hankeKayttajaService.getKayttajatByHankeId(hankeIdentifier.id)
        disclosureLogService.saveDisclosureLogsForHankeKayttajat(users, currentUserId())

        logger.info { "Found ${users.size} kayttajat for ${hankeIdentifier.logString()}" }

        return HankeKayttajaResponse(users)
    }

    @PostMapping("/hankkeet/{hankeTunnus}/kayttajat")
    @Operation(
        summary = "Add a user for the hanke.",
        description =
            "Add a new user for the hanke. Adds an invitation with viewing permissions. Sends an invitation email to them.",
    )
    @ApiResponse(
        description = "The user that was added",
        responseCode = "200",
    )
    @ApiResponse(
        description = "Hanke not found",
        responseCode = "404",
        content = [Content(schema = Schema(implementation = HankeError::class))],
    )
    @ApiResponse(
        description = "User with duplicate email",
        responseCode = "409",
        content = [Content(schema = Schema(implementation = HankeError::class))],
    )
    @PreAuthorize("@hankeKayttajaAuthorizer.authorizeHankeTunnus(#hankeTunnus, 'CREATE_USER')")
    fun createNewUser(
        @RequestBody request: NewUserRequest,
        @PathVariable hankeTunnus: String
    ): HankeKayttajaDto {
        val hanke = hankeService.loadHanke(hankeTunnus)!!
        return hankeKayttajaService.createNewUser(request, hanke, currentUserId())
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
    @PreAuthorize(
        "@hankeKayttajaAuthorizer.authorizeHankeTunnus(#hankeTunnus, 'MODIFY_EDIT_PERMISSIONS')"
    )
    fun updatePermissions(
        @RequestBody permissions: PermissionUpdate,
        @PathVariable hankeTunnus: String
    ) {
        val hankeIdentifier = hankeService.findIdentifier(hankeTunnus)!!

        val userId = currentUserId()

        val deleteAdminPermission =
            permissionService.hasPermission(
                hankeIdentifier.id,
                userId,
                PermissionCode.MODIFY_DELETE_PERMISSIONS
            )

        hankeKayttajaService.updatePermissions(
            hankeIdentifier,
            permissions.kayttajat.associate { it.id to it.kayttooikeustaso },
            deleteAdminPermission,
            userId
        )
    }

    @PostMapping("/kayttajat")
    @Operation(
        summary = "Identify a user",
        description =
            """
Identifies a user who has been invited to a hanke. Finds a token in database
that's the same as the token given in the request. Activates the permission to
the hanke the token was created for.

Removes the token after a successful identification.

Responds with information about the activated user and the hanke associated with it.
"""
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    description = "User identified, permission to hanke given",
                    responseCode = "200",
                ),
                ApiResponse(
                    description = "Token not found or outdated",
                    responseCode = "404",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
                ApiResponse(
                    description =
                        "Token doesn't have a user associated with it or the verified name cannot be retrieved from Profiili",
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
    fun identifyUser(
        @RequestBody tunnistautuminen: Tunnistautuminen,
        @Parameter(hidden = true) @CurrentSecurityContext securityContext: SecurityContext
    ): TunnistautuminenResponse {
        val kayttaja =
            hankeKayttajaService.createPermissionFromToken(
                currentUserId(),
                tunnistautuminen.tunniste,
                securityContext
            )

        val hanke = hankeService.loadHankeById(kayttaja.hankeId)!!
        return TunnistautuminenResponse(kayttaja.id, hanke.hankeTunnus, hanke.nimi)
    }

    @PostMapping("/kayttajat/{kayttajaId}/kutsu")
    @Operation(
        summary = "Resend an invitation email",
        description =
            """
Resend the invitation email the user was sent when they were first added to the
hanke as a contact person.

Regenerates the invitation token and link. This means that the link in the
original email will not work anymore. It also means that the period of validity
of the token and link will be reset.

Returns the updated hankekayttaja.
"""
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    description = "Invitation email resent",
                    responseCode = "204",
                ),
                ApiResponse(
                    description = "User not found",
                    responseCode = "404",
                ),
                ApiResponse(
                    description =
                        "User has already been activated or the current user doesn't have name and email registered.",
                    responseCode = "409",
                    content = [Content(schema = Schema(implementation = HankeError::class))]
                ),
            ]
    )
    @PreAuthorize("@hankeKayttajaAuthorizer.authorizeKayttajaId(#kayttajaId, 'RESEND_INVITATION')")
    fun resendInvitations(@PathVariable kayttajaId: UUID): HankeKayttajaDto =
        hankeKayttajaService.resendInvitation(kayttajaId, currentUserId()).toDto()

    @PutMapping("/hankkeet/{hankeTunnus}/kayttajat/self")
    @Operation(
        summary = "Update the user's own contact information",
        description =
            """
Updates the contact information of the hankekayttaja the user has in the hanke.

The sahkoposti and puhelinnumero are mandatory and can't be empty or blank.

Returns the updated hankekayttaja.
"""
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    description = "Information updated",
                    responseCode = "200",
                ),
                ApiResponse(
                    description = "Email or telephone number is missing",
                    responseCode = "400",
                ),
                ApiResponse(
                    description = "Hanke not found or not authorized",
                    responseCode = "404",
                ),
            ]
    )
    @PreAuthorize("@hankeKayttajaAuthorizer.authorizeHankeTunnus(#hankeTunnus, 'VIEW')")
    fun updateOwnContactInfo(
        @Valid @RequestBody update: ContactUpdate,
        @PathVariable hankeTunnus: String
    ): HankeKayttajaDto {
        return hankeKayttajaService
            .updateOwnContactInfo(hankeTunnus, update, currentUserId())
            .toDto()
    }

    @PutMapping("/hankkeet/{hankeTunnus}/kayttajat/{kayttajaId}")
    @Operation(
        summary = "Update the contact information of a user",
        description =
            """
Updates the contact and (optionally) name information of the hankekayttaja.

Returns the updated hankekayttaja.
"""
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    description = "Information updated",
                    responseCode = "200",
                ),
                ApiResponse(
                    description = "Email or telephone number is missing",
                    responseCode = "400",
                ),
                ApiResponse(
                    description = "Hanke not found or not authorized or user is not in hanke",
                    responseCode = "404",
                ),
                ApiResponse(
                    description =
                        "User has already been activated and therefore cannot update name",
                    responseCode = "409",
                ),
            ]
    )
    @PreAuthorize("@hankeKayttajaAuthorizer.authorizeHankeTunnus(#hankeTunnus, 'MODIFY_USER')")
    fun updateKayttajaInfo(
        @RequestBody @Valid update: KayttajaUpdate,
        @PathVariable hankeTunnus: String,
        @PathVariable kayttajaId: UUID
    ): HankeKayttajaDto {
        return hankeKayttajaService.updateKayttajaInfo(hankeTunnus, update, kayttajaId).toDto()
    }

    @GetMapping("/kayttajat/{kayttajaId}/deleteInfo")
    @Operation(
        summary = "Check if user can be deleted",
        description =
            """
Check if there are active applications the user is a contact in that would
prevent their deletion. Deleting would also be prevented if the user is the only
contact in the applicant customer role, so check that as well.

Also, collect draft applications the user is a contact in, since the frontend
needs to show them when asking for confirmation.

Does not check if the caller needs KAIKKI_OIKEUDET to delete this particular
user.
"""
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    description = "Return the results",
                    responseCode = "200",
                ),
                ApiResponse(
                    description = "kayttajaId is not a proper UUID",
                    responseCode = "400",
                ),
                ApiResponse(
                    description = "Hankekayttaja not found or not authorized",
                    responseCode = "404",
                ),
            ]
    )
    @PreAuthorize("@hankeKayttajaAuthorizer.authorizeKayttajaId(#kayttajaId, 'DELETE_USER')")
    fun checkForDelete(@PathVariable kayttajaId: UUID): HankekayttajaDeleteService.DeleteInfo =
        hankekayttajaDeleteService.checkForDelete(kayttajaId)

    @Operation(
        summary = "Delete the user",
        description =
            """
Deletes the given hankekayttaja (user) from the hanke.

Check if there are active applications the user is a contact in or if the user is the only
contact in the applicant customer role. If either is true, refuse to delete the kayttaja.
"""
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    description = "User deleted. No body.",
                    responseCode = "204",
                ),
                ApiResponse(
                    description = "kayttajaId is not a proper UUID.",
                    responseCode = "400",
                ),
                ApiResponse(
                    description = "Hankekayttaja not found or not authorized.",
                    responseCode = "404",
                ),
                ApiResponse(
                    description =
                        "Hankekayttaja is a contact on an active application or " +
                            "the only contact in the applicant customer role or " +
                            "there would be no admin users left.",
                    responseCode = "409",
                ),
            ]
    )
    @DeleteMapping("/kayttajat/{kayttajaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@hankeKayttajaAuthorizer.authorizeKayttajaId(#kayttajaId, 'DELETE_USER')")
    fun delete(@PathVariable kayttajaId: UUID) {
        logger.info { "Deleting kayttaja $kayttajaId" }
        hankekayttajaDeleteService.delete(kayttajaId, currentUserId())
        logger.info { "Deleted kayttaja $kayttajaId" }
    }

    data class Tunnistautuminen(val tunniste: String)

    data class TunnistautuminenResponse(
        val kayttajaId: UUID,
        val hankeTunnus: String,
        val hankeNimi: String,
    )

    @ExceptionHandler(UserAlreadyExistsException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @Hidden
    fun userAlreadyExistsException(ex: UserAlreadyExistsException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI4006
    }

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
    fun verifiedNameNotFoundException(ex: VerifiedNameNotFound): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI4007
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

    @ExceptionHandler
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @Hidden
    fun hankeKayttajaNotFoundException(ex: HankeKayttajaNotFoundException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI4001
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.CONFLICT)
    @Hidden
    fun currentUserWithoutKayttajaException(ex: CurrentUserWithoutKayttajaException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI4003
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.CONFLICT)
    @Hidden
    fun onlyOmistajaContactException(ex: OnlyOmistajaContactException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI4003
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.CONFLICT)
    @Hidden
    fun hasActiveApplicationsException(ex: HasActiveApplicationsException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI4003
    }
}
