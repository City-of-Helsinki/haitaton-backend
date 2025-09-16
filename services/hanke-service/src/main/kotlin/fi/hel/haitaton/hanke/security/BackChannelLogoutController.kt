package fi.hel.haitaton.hanke.security

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.CacheControl
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

private val logger = mu.KotlinLogging.logger {}

/**
 * Controller for handling backchannel logout requests. By calling this endpoint, Helsinki Profiili
 * will log out the user from all applications.
 *
 * See https://openid.net/specs/openid-connect-backchannel-1_0.html
 */
@RestController
@RequestMapping("/backchannel-logout")
class BackChannelLogoutController(private val logoutService: LogoutService) {

    @PostMapping(
        consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    @Operation(
        summary = "Backhannel logout",
        description =
            "Logs out the user from all Helsinki applications. Called by Helsinki Profiili.",
    )
    @ApiResponses(
        value =
            [
                ApiResponse(
                    description = "Success",
                    responseCode = "200",
                    content =
                        [Content(schema = Schema(implementation = BackChannelLogoutError::class))],
                ),
                ApiResponse(
                    description = "Invalid request",
                    responseCode = "400",
                    content =
                        [Content(schema = Schema(implementation = BackChannelLogoutError::class))],
                ),
                ApiResponse(
                    description = "Internal server error",
                    responseCode = "500",
                    content =
                        [Content(schema = Schema(implementation = BackChannelLogoutError::class))],
                ),
            ]
    )
    fun logout(@RequestBody form: MultiValueMap<String, String?>): ResponseEntity<out Any?> {
        logger.info { "Received backchannel logout request" }
        val logoutToken = form.getFirst("logout_token")
        if (logoutToken == null) {
            logger.error { "Logout token is null" }
            throw IllegalArgumentException("Logout token is null")
        }
        logoutService.logout(logoutToken)
        logger.info { "Succesfully handled backchannel logout request" }
        return ResponseEntity.ok().build()
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException::class)
    @Hidden
    fun handleIllegalArgumentException(
        e: IllegalArgumentException
    ): ResponseEntity<BackChannelLogoutError> =
        ResponseEntity.badRequest()
            .header(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().headerValue)
            .body(BackChannelLogoutError(BackChannelLogoutError.ERROR_INVALID_REQUEST, e.message))

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(JwtException::class)
    @Hidden
    fun handleJwtException(e: JwtException): ResponseEntity<BackChannelLogoutError> =
        ResponseEntity.badRequest()
            .header(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().headerValue)
            .body(BackChannelLogoutError(BackChannelLogoutError.ERROR_INVALID_TOKEN, e.message))

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(Exception::class)
    @Hidden
    fun handleException(e: Exception): ResponseEntity<BackChannelLogoutError> =
        ResponseEntity.internalServerError()
            .header(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().headerValue)
            .body(
                BackChannelLogoutError(
                    BackChannelLogoutError.ERROR_INTERNAL_SERVER_ERROR,
                    e.message,
                )
            )
}

data class BackChannelLogoutError(
    val error: String,
    @JsonProperty("error_description") val errorDescription: String?,
) {
    companion object {
        const val ERROR_INVALID_REQUEST = "InvalidRequest"
        const val ERROR_INVALID_TOKEN = "InvalidToken"
        const val ERROR_INTERNAL_SERVER_ERROR = "InternalServerError"
    }
}
