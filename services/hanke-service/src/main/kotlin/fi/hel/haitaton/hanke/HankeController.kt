package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeSearch
import fi.hel.haitaton.hanke.geometria.HankeGeometriatService
import fi.hel.haitaton.hanke.validation.ValidHanke
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import javax.validation.ConstraintViolationException

private val logger = KotlinLogging.logger { }

@RestController
@RequestMapping("/hankkeet")
@Validated
class HankeController(
    @Autowired private val hankeService: HankeService,
    @Autowired private val hankeGeometriatService: HankeGeometriatService
) {

    /**
     * Get one hanke with hankeTunnus.
     *  TODO: token  from front?
     *  TODO: validation for input parameter
     */
    @GetMapping("/{hankeTunnus}")
    fun getHankeByTunnus(@PathVariable(name = "hankeTunnus") hankeTunnus: String?): ResponseEntity<Any> {
        logger.info {
            "Getting Hanke $hankeTunnus..."
        }
        if (hankeTunnus == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(HankeError.HAI1002)
        }
        return try {
            val hanke = hankeService.loadHanke(hankeTunnus)
            logger.info {
                "Got Hanke $hankeTunnus: ${hanke?.toLogString()}"
            }
            if (hanke == null) {
                ResponseEntity.status(HttpStatus.NOT_FOUND).body(HankeError.HAI1001)
            } else {
                ResponseEntity.status(HttpStatus.OK).body(hanke)
            }
        } catch (e: Exception) {
            logger.error(e) {
                HankeError.HAI1004.toString()
            }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(HankeError.HAI1004)
        }
    }

    @GetMapping
    fun getHankeList(hankeSearch: HankeSearch? = null): ResponseEntity<Any> {
        logger.info {
            "Searching Hankkeet: $hankeSearch"
        }
        return try {
            val hankeList = hankeService.loadAllHanke(hankeSearch)
            if (hankeSearch != null && hankeSearch.includeGeometry()) {
                includeGeometry(hankeList)
            }
            logger.info {
                "Found Hankkeet: ${hankeList.size}"
            }
            logger.debug {
                "Search results: ${hankeList.joinToString("\n") { it.toLogString() }}"
            }
            ResponseEntity.status(HttpStatus.OK).body(hankeList)
        } catch (e: Exception) {
            logger.error(e) {
                HankeError.HAI1004.toString()
            }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(HankeError.HAI1004)
        }
    }

    private fun includeGeometry(hankeList: List<Hanke>) {
        hankeList.forEach { hanke ->
            hanke.geometriat = hankeGeometriatService.loadGeometriat(hanke)
        }
    }

    /**
     * Add one hanke.
     * This method will be called when we do not have id for hanke yet
     */
    @PostMapping
    fun createHanke(@ValidHanke @RequestBody hanke: Hanke?): ResponseEntity<Any> {
        logger.info {
            "Creating Hanke: ${hanke?.toLogString()}"
        }

        if (hanke == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(HankeError.HAI1002)
        }

        return try {
            val createdHanke = hankeService.createHanke(hanke)
            logger.info { "Created Hanke ${createdHanke.hankeTunnus}." }
            ResponseEntity.status(HttpStatus.OK).body(createdHanke)
        } catch (e: Exception) {
            logger.error(e) {
                HankeError.HAI1003.toString()
            }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(HankeError.HAI1003)
        }
    }

    /**
     * Update one hanke.
     *  TODO: user from front?
     */
    @PutMapping("/{hankeTunnus}")
    fun updateHanke(@ValidHanke @RequestBody hanke: Hanke?, @PathVariable hankeTunnus: String?): ResponseEntity<Any> {
        logger.info {
            "Updating Hanke: ${hanke?.toLogString()}"
        }
        if (hanke == null || hankeTunnus == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(HankeError.HAI1002)
        }
        if (hankeTunnus != hanke.hankeTunnus) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(HankeError.HAI1002)
        }

        return try {
            val updatedHanke = hankeService.updateHanke(hanke)
            logger.info {
                "Updated hanke ${updatedHanke.hankeTunnus}."
            }
            ResponseEntity.status(HttpStatus.OK).body(updatedHanke)
        } catch (e: Exception) {
            logger.error(e) {
                HankeError.HAI1003.toString()
            }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(HankeError.HAI1003)
        }
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleValidationExceptions(ex: ConstraintViolationException): HankeError {
        val violation = ex.constraintViolations.firstOrNull { constraintViolation ->
            constraintViolation.message.matches(HankeError.CODE_PATTERN)
        }
        return if (violation != null) {
            HankeError.valueOf(violation)
        } else {
            HankeError.HAI1002
        }
    }

    @ResponseStatus(HttpStatus.FORBIDDEN)
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(ex: AccessDeniedException): HankeError = HankeError.HAI0001
}
