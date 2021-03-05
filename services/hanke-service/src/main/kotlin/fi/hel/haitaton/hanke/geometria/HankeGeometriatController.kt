package fi.hel.haitaton.hanke.geometria

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeError.Companion.CODE_PATTERN
import fi.hel.haitaton.hanke.HankeNotFoundException
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import javax.validation.ConstraintViolationException

private val logger = KotlinLogging.logger { }

@RestController
@RequestMapping("/hankkeet")
@Validated
class HankeGeometriaController(@Autowired private val service: HankeGeometriatService) {

    @PostMapping("/{hankeTunnus}/geometriat")
    fun createGeometria(
        @PathVariable("hankeTunnus") hankeTunnus: String,
        @ValidHankeGeometriat @RequestBody hankeGeometriat: HankeGeometriat?
    ): ResponseEntity<Any> {
        logger.info {
            "Saving Hanke Geometria for $hankeTunnus..."
        }
        if (hankeGeometriat == null) {
            return ResponseEntity.badRequest().body(HankeError.HAI1011)
        }
        return try {
            val savedHankeGeometriat = service.saveGeometriat(hankeTunnus, hankeGeometriat)
            ResponseEntity.ok(savedHankeGeometriat)
        } catch (e: IllegalArgumentException) {
            logger.error {
                e.message
            }
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(HankeError.HAI1002)
        } catch (e: HankeNotFoundException) {
            logger.error {
                e.message
            }
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(HankeError.HAI1001)
        } catch (e: Exception) {
            logger.error(e) {
                HankeError.HAI1012.toString()
            }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(HankeError.HAI1012)
        }
    }

    @GetMapping("/{hankeTunnus}/geometriat")
    fun getGeometria(@PathVariable("hankeTunnus") hankeTunnus: String): ResponseEntity<Any> {
        logger.info {
            "Getting Hanke Geometria for $hankeTunnus..."
        }
        return try {
            val geometry = service.loadGeometriat(hankeTunnus)
            if (geometry == null) {
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(HankeError.HAI1015)
            } else {
                ResponseEntity.ok(geometry)
            }
        } catch (e: HankeNotFoundException) {
            logger.error {
                e.message
            }
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(HankeError.HAI1001)
        } catch (e: Exception) {
            logger.error(e) {
                HankeError.HAI1014.toString()
            }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(HankeError.HAI1014)
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleValidationExceptions(ex: ConstraintViolationException): HankeError {
        val violation = ex.constraintViolations.firstOrNull { constraintViolation ->
            constraintViolation.message.matches(CODE_PATTERN)
        }
        return if (violation != null) {
            HankeError.valueOf(violation)
        } else {
            HankeError.HAI1011
        }
    }
}
