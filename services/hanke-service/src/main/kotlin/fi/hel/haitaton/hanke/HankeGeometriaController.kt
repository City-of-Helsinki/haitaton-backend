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
            return ResponseEntity.badRequest().body(HankeError("HAI1011", "Invalid Hanke geometry"))
        }
        return try {
            service.saveGeometria(hankeId, hankeGeometria)
            ResponseEntity.noContent().build()
        } catch (e: HankeNotFoundException) {
            logger.error(e) {
                "HAI1001 - Hanke not found"
            }
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(HankeError("HAI1001", "Hanke not found"))
        } catch (e: Exception) {
            logger.error(e) {
                "HAI1012 - Internal error while saving Hanke geometry"
            }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(HankeError("HAI1012", "Internal error while saving Hanke $hankeId geometry"))
        }
    }

    @GetMapping("/{hankeId}/geometriat")
    fun getGeometria(@PathVariable("hankeId") hankeId: String): ResponseEntity<Any> {
        return try {
            val geometry = service.loadGeometria(hankeId)
            if (geometry == null) {
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(HankeError("HAI1015", "Hanke geometry not found"))
            } else {
                ResponseEntity.ok(geometry)
            }
        } catch (e: HankeNotFoundException) {
            logger.error(e) {
                "HAI1001 - Hanke not found"
            }
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(HankeError("HAI1001", "Hanke not found"))
        } catch (e: Exception) {
            logger.error(e) {
                "HAI1014 - Internal error while loading Hanke geometry"
            }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(HankeError("HAI1014", "Internal error while loading Hanke $hankeId geometry"))
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleValidationExceptions(ex: ConstraintViolationException): HankeError {
        val violation = ex.constraintViolations.firstOrNull { constraintViolation ->
            constraintViolation.message.startsWith("HAI") && constraintViolation.message.contains(':')
        }
        return if (violation != null) {
            HankeError(violation)
        } else {
            HankeError("HAI1011", "Invalid Hanke geometry")
        }
    }
}