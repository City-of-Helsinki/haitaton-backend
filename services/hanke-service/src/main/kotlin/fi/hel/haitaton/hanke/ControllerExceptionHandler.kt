package fi.hel.haitaton.hanke

import io.sentry.Sentry
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

private val logger = KotlinLogging.logger { }

@RestControllerAdvice
class ControllerExceptionHandler {

    @ExceptionHandler(HankeNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun hankeNotFound(ex: HankeNotFoundException): HankeError {
        logger.warn {
            ex.message
        }
        // notify Sentry
        Sentry.captureException(ex)
        return HankeError.HAI1001
    }

    @ExceptionHandler(HankeYhteystietoNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun hankeYhteystietoNotFound(ex: HankeYhteystietoNotFoundException): HankeError {
        logger.warn {
            ex.message
        }
        // notify Sentry
        Sentry.captureException(ex)
        return HankeError.HAI1020
    }

    @ExceptionHandler(TormaystarkasteluAlreadyCalculatedException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun tormaystarkasteluAlreadyCalculatedException(ex: TormaystarkasteluAlreadyCalculatedException): HankeError {
        logger.warn {
            ex.message
        }
        return HankeError.HAI1009
    }

    @ExceptionHandler(IllegalArgumentException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun illegalArgumentException(ex: IllegalArgumentException): HankeError {
        logger.error(ex) {
            ex.message
        }
        // notify Sentry
        Sentry.captureException(ex)
        return HankeError.HAI0003
    }

    @ExceptionHandler(Throwable::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun throwable(ex: Throwable): HankeError {
        logger.error(ex) {
            ex.message
        }
        // notify Sentry
        Sentry.captureException(ex)
        return HankeError.HAI0002
    }
}
