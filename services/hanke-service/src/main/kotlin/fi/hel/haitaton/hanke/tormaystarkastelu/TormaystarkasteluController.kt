package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.geometria.HankeGeometriatService
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger { }

@RestController
@RequestMapping("/hankkeet")
class TormaystarkasteluController(
        @Autowired private val hankeService: HankeService,
        @Autowired private val hankeGeometriatService: HankeGeometriatService) {

    @GetMapping("/{hankeTunnus}/tormaystarkastelu")
    fun getTormaysTarkastelu(@PathVariable(name = "hankeTunnus") hankeTunnus: String): ResponseEntity<Any> {
        logger.info {
            "Fetching existing tormaystarkastelu for hanke: $hankeTunnus"
        }

        val hanke = hankeService.loadHanke(hankeTunnus) ?:
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(HankeError.HAI1002)

        if (hanke.tormaystarkasteluTulos == null) {
            logger.info { "Tormaystarkastelu does not exist for Hanke $hankeTunnus." }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(HankeError.HAI1007)
        }

        logger.info { "Tormaystarkastelu fetched for Hanke $hankeTunnus." }
        return ResponseEntity.status(HttpStatus.OK).body(hanke.tormaystarkasteluTulos)
    }

    @Deprecated("Tormaystarkastelu to be calculated when updating hanke")
    @PostMapping("/{hankeTunnus}/tormaystarkastelu")
    fun createTormaysTarkasteluForHanke(@PathVariable(name = "hankeTunnus") hankeTunnus: String): ResponseEntity<Any> {
        val hanke = hankeService.loadHanke(hankeTunnus)
                ?: return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(HankeError.HAI1002)

        if (hanke.tormaystarkasteluTulos != null) {
            // Tormaystarkastelu is already calculated
            // and it is up-to-date as there's no way to update
            // hanke without triggering recalculation
            return ResponseEntity.status(HttpStatus.OK).body(hanke)
        }

        val hankeGeometriat = hankeGeometriatService.loadGeometriat(hanke)
                ?: return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(HankeError.HAI1015)
        hanke.geometriat = hankeGeometriat

        val updatedHanke = hankeService.updateHanke(hanke)
        logger.info { "tormaystarkastelu created for Hanke ${updatedHanke.hankeTunnus}." }
        return ResponseEntity.status(HttpStatus.OK).body(updatedHanke)
    }
}
