package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.validation.ValidHanke
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*


private val logger = KotlinLogging.logger { }

@RestController
@RequestMapping("/tormays")
@Validated
class TormaystarkasteluController {

    @GetMapping("/{hankeTunnus}")
    fun getTormaysTarkastelu(@PathVariable(name = "hankeTunnus") hankeTunnus: String?): ResponseEntity<Any> {
        logger.info {
            "Creating tormaystarkastelu for hanke: ${hankeTunnus}"
        }

        if (hankeTunnus == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(HankeError.HAI1002) //TODO: own error code?
        }

        return try {
            //call service

            val tormaysResults = getDummyTormaystarkasteluTulos()


            // has saved tormaystulos to database
            //return hanke with tormaystulos
            logger.info { "tormaystarkastelu created for Hanke ${hankeTunnus}." }
            ResponseEntity.status(HttpStatus.OK).body(tormaysResults)
        } catch (e: Exception) {
            logger.error(e) {

                HankeError.HAI1006.toString()

            }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(HankeError.HAI1006)
        }
    }

    private fun getDummyTormaystarkasteluTulos(): TormaystarkasteluTulos {
        val tormaysResults = TormaystarkasteluTulos()
        tormaysResults.hankeId = 2 //TODO: selvitettävä jostain
        tormaysResults.joukkoliikenneIndeksi = 3.4
        tormaysResults.pyorailyIndeksi = 4.2
        tormaysResults.perusIndeksi = 2.1

        tormaysResults.liikennehaittaIndeksi = LiikennehaittaIndeksiType()
        tormaysResults.liikennehaittaIndeksi!!.indeksi = 4.2
        tormaysResults.liikennehaittaIndeksi!!.type = IndeksiType.PYORAILYINDEKSI
        return tormaysResults
    }

    @PostMapping
    fun createTormaysTarkasteluForHanke(@ValidHanke @RequestBody hanke: Hanke?): ResponseEntity<Any> {
        logger.info {
            "Creating tormaystarkastelu for hanke: ${hanke?.toLogString()}"
        }

        if (hanke == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(HankeError.HAI1002)
        }

        return try {
            //if(hanke.tilat.onTiedotLiikenneHaittaIndeksille)
            // then calculate:
            // - call service to create tormays
            // - service has saved tormaystulos to database and saved hanke.onLiikenneHaittaIndeksi=true
            // - return hanke with tormaystulos
            val hankeWithTormaysResults: Hanke = hanke.copy()
            var dummyTulos = getDummyTormaystarkasteluTulos()
            hankeWithTormaysResults.tormaystarkasteluTulos = dummyTulos

            //miten törmäystulos? Onko se erikseen hankkeella?

            logger.info { "tormaystarkastelu created for Hanke ${hankeWithTormaysResults.hankeTunnus}." }
            ResponseEntity.status(HttpStatus.OK).body(hankeWithTormaysResults)
        } catch (e: Exception) {
            logger.error(e) {

                HankeError.HAI1006.toString()

            }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(HankeError.HAI1006)
        }
    }


}