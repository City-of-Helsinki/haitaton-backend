package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.validation.ValidHanke
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.*
import javax.validation.ConstraintViolationException


private val logger = KotlinLogging.logger { }

@RestController
@RequestMapping("/hankkeet")
@Validated
class HankeController(@Autowired private val hankeService: HankeService) {

    /**
     * Get one hanke with hankeTunnus.
     *  TODO: token  from front?
     *  TODO: validation for input parameter
     */
    @GetMapping("/{hankeTunnus}")
    fun getHankeByTunnus(@PathVariable(name = "hankeTunnus") hankeTunnus: String?): ResponseEntity<Any> {
        if (hankeTunnus == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(HankeError.HAI1002)
        }
        return try {
            val hanke = hankeService.loadHanke(hankeTunnus)
            ResponseEntity.status(HttpStatus.OK).body(hanke)

        } catch (e: HankeNotFoundException) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(HankeError.HAI1001)

        } catch (e: Exception) {
            logger.error(e) {
                HankeError.HAI1004.toString()
            }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(HankeError.HAI1004)
        }
    }

    /**
     * Get all hanke
     *  TODO: token  from front?
     *  TODO: limit call with user information and return only user's own hanke or something?
     *  TODO: do we later on have users who can read all Hanke items?
     */
    fun getAllHankeItems(): ResponseEntity<Any> {
        logger.info { "Entering getAllHankeItems" }
        return try {
            val hankeList = hankeService.loadAllHanke()
            ResponseEntity.status(HttpStatus.OK).body(hankeList)

        } catch (e: Exception) {
            logger.error(e) {
                HankeError.HAI1004.toString()
            }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(HankeError.HAI1004)
        }
    }

    @GetMapping
    fun getHankeList(@RequestParam(name = "periodBegin") @DateTimeFormat(pattern = "yyyy-MM-dd") periodBegin: LocalDate?,
       @RequestParam(name = "periodEnd") @DateTimeFormat(pattern = "yyyy-MM-dd") periodEnd: LocalDate?): ResponseEntity<Any> {

        if (periodBegin == null || periodEnd == null) {
            return getAllHankeItems()
        }
        return try {
            val hankeList = hankeService.loadAllHankeBetweenDates(periodBegin, periodEnd)
            ResponseEntity.status(HttpStatus.OK).body(hankeList)

        } catch (e: Exception) {
            logger.error(e) {
                HankeError.HAI1004.toString()
            }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(HankeError.HAI1004)
        }
    }

    /**
     * Get all hanke datas within time period (= either or both of alkuPvm and loppuPvm are inside the requested period)
     *  TODO: user token  from front?
     *  TODO: do we limit result for user own hanke?
     */
 //   @RequestMapping( method = arrayOf(RequestMethod.GET))
    fun getHankeByPeriodOfTime(@RequestParam(name = "periodBegin") @DateTimeFormat(pattern = "yyyy-MM-dd") periodBegin: LocalDate?,
                               @RequestParam(name = "periodEnd") @DateTimeFormat(pattern = "yyyy-MM-dd") periodEnd: LocalDate?): ResponseEntity<Any> {

        logger.info { "getHankeByPeriodOfTime with range: " + periodBegin + " - " + periodEnd }
        if (periodBegin == null || periodEnd == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(HankeError.HAI1002)
        }
        return try {
            val hankeList = hankeService.loadAllHankeBetweenDates(periodBegin, periodEnd)
            ResponseEntity.status(HttpStatus.OK).body(hankeList)

        } catch (e: Exception) {
            logger.error(e) {
                HankeError.HAI1004.toString()
            }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(HankeError.HAI1004)
        }
    }


    /**
     * Add one hanke.
     * This method will be called when we do not have id for hanke yet
     */
    @PostMapping
    fun createHanke(@ValidHanke @RequestBody hanke: Hanke?): ResponseEntity<Any> {
        logger.info { "Entering createHanke ${hanke?.toJsonString()}" }

        if (hanke == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(HankeError.HAI1002)
        }

        return try {
            val createdHanke = hankeService.createHanke(hanke)
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
        logger.info { "Entering update Hanke $hankeTunnus : ${hanke?.toJsonString()}" }
        if (hanke == null || hankeTunnus == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(HankeError.HAI1002)
        }
        if (!hankeTunnus.equals(hanke.hankeTunnus)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(HankeError.HAI1002)
        }

        return try {
            val createdHanke = hankeService.updateHanke(hanke)
            ResponseEntity.status(HttpStatus.OK).body(createdHanke)
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

}
