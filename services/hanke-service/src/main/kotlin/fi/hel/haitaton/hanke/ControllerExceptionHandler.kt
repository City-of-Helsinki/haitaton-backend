package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.geometria.GeometriaValidationException
import fi.hel.haitaton.hanke.geometria.UnsupportedCoordinateSystemException
import io.sentry.Sentry
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

private val logger = KotlinLogging.logger {}

@RestControllerAdvice
class ControllerExceptionHandler {

    @ExceptionHandler(HankeNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun hankeNotFound(ex: HankeNotFoundException): HankeError {
        logger.warn { ex.message }
        // notify Sentry
        Sentry.captureException(ex)
        return HankeError.HAI1001
    }

    @ExceptionHandler(HankeYhteystietoNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun hankeYhteystietoNotFound(ex: HankeYhteystietoNotFoundException): HankeError {
        logger.warn { ex.message }
        // notify Sentry
        Sentry.captureException(ex)
        return HankeError.HAI1020
    }

    @ExceptionHandler(HankeYhteystietoProcessingRestrictedException::class)
    // Using 451 (since the restriction is typically due to legal reasons.
    // However, in some cases 403 forbidden might be considered correct response, too.
    @ResponseStatus(HttpStatus.UNAVAILABLE_FOR_LEGAL_REASONS)
    fun hankeYhteystietoProcessingRestricted(
        ex: HankeYhteystietoProcessingRestrictedException
    ): HankeError {
        logger.warn { ex.message }
        // TODO: the response body SHOULD include an explanation and link to server;
        //  left as future exercise. See https://tools.ietf.org/html/rfc7725
        return HankeError.HAI1029
    }

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun illegalArgumentException(ex: IllegalArgumentException): HankeError {
        logger.error(ex) { ex.message }
        // notify Sentry
        Sentry.captureException(ex)
        return HankeError.HAI0003
    }

    @ExceptionHandler(GeometriaValidationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun invalidGeometria(ex: GeometriaValidationException): HankeError {
        logger.warn { ex.message }
        // notify Sentry
        Sentry.captureException(ex)
        return HankeError.HAI1011
    }

    @ExceptionHandler(UnsupportedCoordinateSystemException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun unsupportedCoordinateSystem(ex: UnsupportedCoordinateSystemException): HankeError {
        logger.warn { ex.message }
        // notify Sentry
        Sentry.captureException(ex)
        return HankeError.HAI1011
    }

    @ExceptionHandler(Throwable::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun throwable(ex: Throwable): HankeError {
        logger.error(ex) { ex.message }
        // notify Sentry
        Sentry.captureException(ex)
        return HankeError.HAI0002
    }
}
