package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.attachment.application.ApplicationInAlluException
import fi.hel.haitaton.hanke.attachment.common.AttachmentInvalidException
import fi.hel.haitaton.hanke.attachment.common.AttachmentLimitReachedException
import fi.hel.haitaton.hanke.attachment.common.AttachmentNotFoundException
import fi.hel.haitaton.hanke.attachment.common.ValtakirjaForbiddenException
import fi.hel.haitaton.hanke.geometria.GeometriaValidationException
import fi.hel.haitaton.hanke.geometria.UnsupportedCoordinateSystemException
import fi.hel.haitaton.hanke.hakemus.HakemusAlreadyProcessingException
import fi.hel.haitaton.hanke.hakemus.HakemusGeometryException
import fi.hel.haitaton.hanke.hakemus.HakemusGeometryNotInsideHankeException
import fi.hel.haitaton.hanke.hakemus.HakemusInWrongStatusException
import fi.hel.haitaton.hanke.hakemus.HakemusNotFoundException
import fi.hel.haitaton.hanke.hakemus.InvalidHakemusyhteyshenkiloException
import fi.hel.haitaton.hanke.hakemus.InvalidHakemusyhteystietoException
import fi.hel.haitaton.hanke.hakemus.InvalidHiddenRegistryKey
import fi.hel.haitaton.hanke.hakemus.WrongHakemusTypeException
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusAlreadySentException
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
import org.springframework.web.reactive.function.client.WebClientResponseException
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
    @ApiResponse(description = "Request’s credentials are missing or invalid", responseCode = "401")
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

    @ExceptionHandler(HankeAlreadyCompletedException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @Hidden
    fun hankeAlreadyCompleted(ex: HankeAlreadyCompletedException): HankeError {
        logger.warn { ex.message }
        Sentry.captureException(ex)
        return HankeError.HAI1034
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

    @ExceptionHandler(HakemusInWrongStatusException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @Hidden
    fun hakemusInWrongStatusException(ex: HakemusInWrongStatusException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2015
    }

    @ExceptionHandler(HakemusGeometryException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun applicationGeometryException(ex: HakemusGeometryException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2005
    }

    @ExceptionHandler(HakemusGeometryNotInsideHankeException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun applicationGeometryNotInsideHankeException(
        ex: HakemusGeometryNotInsideHankeException
    ): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2007
    }

    @ExceptionHandler(InvalidHakemusyhteystietoException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun invalidHakemusyhteystietoException(ex: InvalidHakemusyhteystietoException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2010
    }

    @ExceptionHandler(InvalidHakemusyhteyshenkiloException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun invalidHakemusyhteyshenkiloException(ex: InvalidHakemusyhteyshenkiloException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2011
    }

    @ExceptionHandler(WrongHakemusTypeException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun wrongHakemusTypeException(ex: WrongHakemusTypeException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2002
    }

    @ExceptionHandler(InvalidHiddenRegistryKey::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @Hidden
    fun invalidHiddenRegistryKey(ex: InvalidHiddenRegistryKey): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI2010
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

    @ExceptionHandler(ValtakirjaForbiddenException::class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    @Hidden
    fun valtakirjaForbiddenException(ex: ValtakirjaForbiddenException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI3004
    }

    @ExceptionHandler(MuutosilmoitusAlreadySentException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    @Hidden
    fun muutosilmoitusAlreadySentException(ex: MuutosilmoitusAlreadySentException): HankeError {
        logger.warn(ex) { ex.message }
        return HankeError.HAI7002
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

    @ExceptionHandler(WebClientResponseException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @Hidden
    fun webClientResponseException(ex: WebClientResponseException): HankeError {
        logger.error { ex.message }
        logger.error { "Response status: ${ex.statusCode}" }
        logger.error { "Error response: ${ex.responseBodyAsString}" }
        Sentry.captureException(ex)
        return HankeError.HAI0002
    }

    @ExceptionHandler(Throwable::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ApiResponse(
        description = "There has been an unexpected error during the call",
        responseCode = "500",
    )
    fun throwable(ex: Throwable): HankeError {
        logger.error(ex) { ex.message }
        Sentry.captureException(ex)
        return HankeError.HAI0002
    }

    class FakeAuthorizationException : RuntimeException()
}

class EndpointDisabledException : RuntimeException("Endpoint disabled in this environment.")
