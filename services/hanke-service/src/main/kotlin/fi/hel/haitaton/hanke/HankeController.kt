package fi.hel.haitaton.hanke

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger { }

@RestController
@RequestMapping("/hankkeet")
class HankeController(@Autowired private val hankeService: HankeService) {

    /**
     * Get one hanke with hankeId.
     *  TODO: token and user from front?
     *  TODO: validation for input
     *
     */
    @GetMapping
    fun getHankeById(@RequestParam(name = "hankeId") hankeId: String?): ResponseEntity<Any> {

        if (hankeId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(HankeError.HAI1017)
        }
        return try {
            val hanke = hankeService.loadHanke(hankeId)

            ResponseEntity.status(HttpStatus.OK).body(hanke)

        } catch (e: HankeNotFoundException) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(HankeError.HAI1001)

        } catch (e: Exception) {
            logger.error(e) {
                HankeError.HAI1016.toString()
            }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(HankeError.HAI1016)
        }
    }


    /**
     * Add one hanke.
     *  TODO: user from front?
     *  TODO: validation for input
     * This method will be called when we do not have id for hanke yet
     */
    @PostMapping
    fun createHanke(@RequestBody hanke: Hanke?): ResponseEntity<Any> {

        logger.info { "Entering createHanke ${hanke?.toJsonString()}" }

        if (hanke == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(HankeError.HAI1017)
        }

        return saveHanke(hanke)
    }

    /**
     * Update one hanke.
     *  TODO: user from front?
     *  TODO: validation for input
     */
    @PutMapping("/{hankeId}")
    fun updateHanke(@RequestBody hanke: Hanke?, @PathVariable hankeId: String?): ResponseEntity<Any> {

        logger.info { "Entering update Hanke $hankeId : ${hanke?.toJsonString()}" }
        if (hanke == null || hankeId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(HankeError.HAI1017)
        }
        return saveHanke(hanke)
    }

    private fun saveHanke(hanke: Hanke): ResponseEntity<Any> {
        return try {
            val createdHanke = hankeService.save(hanke)
            ResponseEntity.status(HttpStatus.OK).body(createdHanke)
        } catch (e: Exception) {
            logger.error(e) {
                HankeError.HAI1016.toString()
            }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(HankeError.HAI1016)
        }
    }

}