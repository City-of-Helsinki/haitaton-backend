package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.validation.ValidFeatureCollection
import mu.KotlinLogging
import org.geojson.FeatureCollection
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import javax.validation.ConstraintViolationException


private val logger = KotlinLogging.logger { }

@RestController
@RequestMapping("/hankkeet")
@Validated
class HankeGeometriaController(@Autowired private val service: HankeGeometriaService) {

    @PostMapping("/{hankeId}/geometriat")
    fun createGeometria(@PathVariable("hankeId") hankeId: String, @ValidFeatureCollection @RequestBody hankeGeometria: FeatureCollection?): ResponseEntity<Any> {
        if (hankeGeometria == null) {
            return ResponseEntity.badRequest().body(HankeError.HAI1011)
        }
        return try {
            service.saveGeometria(hankeId, hankeGeometria)
            ResponseEntity.noContent().build()
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

    @GetMapping("/{hankeId}/geometriat")
    fun getGeometria(@PathVariable("hankeId") hankeId: String): ResponseEntity<Any> {
        return try {
            val geometry = service.loadGeometria(hankeId)
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
            constraintViolation.message.matches("HAI\\d{4}".toRegex())
        }
        return if (violation != null) {
            HankeError.valueOf(violation)
        } else {
            HankeError.HAI1011
        }
    }
}