package fi.hel.haitaton.hanke.geometria

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.toHankeError
import javax.validation.ConstraintViolationException
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
        val savedHankeGeometriat = service.saveGeometriat(hankeTunnus, hankeGeometriat)
        return ResponseEntity.ok(savedHankeGeometriat)
    }

    @GetMapping("/{hankeTunnus}/geometriat")
    fun getGeometria(@PathVariable("hankeTunnus") hankeTunnus: String): ResponseEntity<Any> {
        logger.info {
            "Getting Hanke Geometria for $hankeTunnus..."
        }
        val geometry = service.loadGeometriat(hankeTunnus)
        return if (geometry == null) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(HankeError.HAI1015)
        } else {
            ResponseEntity.ok(geometry)
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleValidationExceptions(ex: ConstraintViolationException): HankeError {
        return ex.toHankeError(HankeError.HAI1011)
    }
}
