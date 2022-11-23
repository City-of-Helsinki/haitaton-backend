package fi.hel.haitaton.hanke.geometria

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeService
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
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/hankkeet")
@Validated
class HankeGeometriaController(
    @Autowired private val hankeService: HankeService,
    @Autowired private val geometryService: HankeGeometriatService
) {

    @GetMapping("/{hankeTunnus}/geometriat")
    fun getGeometria(@PathVariable("hankeTunnus") hankeTunnus: String): ResponseEntity<Any> {
        logger.info { "Getting Hanke Geometria for $hankeTunnus..." }
        val hanke =
            hankeService.loadHanke(hankeTunnus)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(HankeError.HAI1001)
        val geometry =
            geometryService.loadGeometriat(hanke)
                ?: return ResponseEntity.status(HttpStatus.NOT_FOUND).body(HankeError.HAI1015)
        return ResponseEntity.ok(geometry)
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleValidationExceptions(ex: ConstraintViolationException): HankeError {
        logger.warn { ex.message }
        return ex.toHankeError(HankeError.HAI1011)
    }
}
