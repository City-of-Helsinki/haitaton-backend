package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.attachment.application.ApplicationInAlluException
import fi.hel.haitaton.hanke.attachment.common.AttachmentInvalidException
import fi.hel.haitaton.hanke.attachment.common.AttachmentLimitReachedException
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.geometria.GeometriaValidationException
import fi.hel.haitaton.hanke.geometria.UnsupportedCoordinateSystemException
import fi.hel.haitaton.hanke.hakemus.HakemusAlreadyProcessingException
import fi.hel.haitaton.hanke.hakemus.HakemusNotFoundException
import io.sentry.Sentry
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.responses.ApiResponse
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.resource.NoResourceFoundException

private val logger = KotlinLogging.logger {}

@RestControllerAdvice
class ControllerExceptionHandler {

    @ExceptionHandler(EndpointDisabledException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @Hidden
    fun endpointDisabled(ex: EndpointDisabledException): HankeError {
        logger.warn { ex.message }
        Sentry.captureException(ex)
        return HankeError.HAI0004
    }

    /** This is a horrible hack to get the 401 error to all endpoints in OpenAPI docs. */
    @ExceptionHandler(FakeAuthorizationException::class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ApiResponse(
        description = "Request’s credentials are missing or invalid",
        responseCode = "401",
    )
    fun forDocumentation(): HankeError = HankeError.HAI0001

    @ExceptionHandler(HankeNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @Hidden
    fun hankeNotFound(ex: HankeNotFoundException): HankeError {
        logger.warn { ex.message }
        Sentry.captureException(ex)
        return HankeError.HAI1001
    }

    @ExceptionHandler(HankeYhteystietoNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @Hidden
    fun hankeYhteystietoNotFound(ex: HankeYhteystietoNotFoundException): HankeError {
        logger.warn { ex.message }
        Sentry.captureException(ex)
        return HankeError.HAI1020
    }

    @ExceptionHandler(HankeYhteystietoProcessingRestrictedException::class)
    // Using 451 (since the restriction is typically due to legal reasons).
    // However, in some cases 403 forbidden might be considered correct response, too.
    @ResponseStatus(HttpStatus.UNAVAILABLE_FOR_LEGAL_REASONS)
    @Hidden
    fun hankeYhteystietoProcessingRestricted(
        ex: HankeYhteystietoProcessingRestrictedException
    ): HankeError {
        logger.warn { ex.message }
        // TODO: the response body SHOULD include an explanation and link to server;
        //  left as future exercise. See https://tools.ietf.org/html/rfc7725
        return HankeError.HAI1029
    }

    @ExceptionHandler(HankeAlluConflictException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @Hidden
    fun hankeAlluConflictException(ex: HankeAlluConflictException): HankeError {
        logger.warn { ex.message }
        return HankeError.HAI2003
    }

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun illegalArgumentException(ex: IllegalArgumentException): HankeError {
        logger.error(ex) { ex.message }
        Sentry.captureException(ex)
        return HankeError.HAI0003
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun methodArgumentTypeMismatchException(ex: MethodArgumentTypeMismatchException): HankeError {
        logger.error(ex) { ex.message }
        Sentry.captureException(ex)
        return HankeError.HAI0003
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun httpMessageNotReadableException(ex: HttpMessageNotReadableException): HankeError {
        logger.error(ex) { ex.message }
        Sentry.captureException(ex)
        return HankeError.HAI0003
    }

    @ExceptionHandler(GeometriaValidationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun invalidGeometria(ex: GeometriaValidationException): HankeError {
        logger.warn { ex.message }
        Sentry.captureException(ex)
        return HankeError.HAI1011
    }

    @ExceptionHandler(UnsupportedCoordinateSystemException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun unsupportedCoordinateSystem(ex: UnsupportedCoordinateSystemException): HankeError {
        logger.warn { ex.message }
        Sentry.captureException(ex)
        return HankeError.HAI1013
    }

    @ExceptionHandler(HakemusNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @Hidden
    fun applicationNotFound(ex: HakemusNotFoundException): HankeError {
        logger.warn { ex.message }
        Sentry.captureException(ex)
        return HankeError.HAI2001
    }

    @ExceptionHandler(ApplicationInAlluException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @Hidden
    fun applicationInAllu(ex: ApplicationInAlluException): HankeError {
        logger.warn { ex.message }
        Sentry.captureException(ex)
        return HankeError.HAI2009
    }

    @ExceptionHandler(HakemusAlreadyProcessingException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @Hidden
    fun applicationAlreadyProcessing(ex: HakemusAlreadyProcessingException): HankeError {
        logger.warn { ex.message }
        Sentry.captureException(ex)
        return HankeError.HAI2003
    }

    @ExceptionHandler(AttachmentNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @Hidden
    fun attachmentArgumentException(ex: AttachmentNotFoundException): HankeError {
        logger.warn { ex.message }
        Sentry.captureException(ex)
        return HankeError.HAI3002
    }

    @ExceptionHandler(AttachmentInvalidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun attachmentUploadException(ex: AttachmentInvalidException): HankeError {
        logger.warn { ex.message }
        Sentry.captureException(ex)
        return HankeError.HAI3001
    }

    @ExceptionHandler(AttachmentLimitReachedException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun attachmentLimitReached(ex: AttachmentLimitReachedException): HankeError {
        logger.warn { ex.message }
        Sentry.captureException(ex)
        return HankeError.HAI3003
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun methodArgumentNotValidException(ex: MethodArgumentNotValidException): HankeError {
        logger.warn { ex.message }
        Sentry.captureException(ex)
        return HankeError.HAI0003
    }

    @ExceptionHandler(NoResourceFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    @Hidden
    fun noResourceFoundException(ex: NoResourceFoundException): HankeError {
        logger.warn { ex.message }
        Sentry.captureException(ex)
        return HankeError.HAI0004
    }

    @ExceptionHandler(Throwable::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ApiResponse(
        description = "There has been an unexpected error during the call",
        responseCode = "500"
    )
    fun throwable(ex: Throwable): HankeError {
        logger.error(ex) { ex.message }
        Sentry.captureException(ex)
        return HankeError.HAI0002
    }

    class FakeAuthorizationException : RuntimeException()
}

class EndpointDisabledException : RuntimeException("Endpoint disabled in this environment.")
