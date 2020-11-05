package fi.hel.haitaton.hanke

import mu.KotlinLogging
import org.geojson.FeatureCollection
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger { }

@RestController
@RequestMapping("/hankkeet")
class HankeGeometriaController(@Autowired private val service: HankeService) {

    @PostMapping("/{hankeId}/geometriat")
    fun createGeometria(@PathVariable("hankeId") hankeId: String, @RequestBody hankeGeometria: FeatureCollection?): ResponseEntity<Any> {
        if (hankeGeometria == null) {
            return ResponseEntity.badRequest().body(HankeError("HAI1011", "Invalid Hanke $hankeId geometry"))
        }
        try {
            service.saveGeometria(hankeId, hankeGeometria)
        } catch (e: HankeNotFoundException) {
            logger.error(e) {
                "HAI1001 - Hanke $hankeId not found"
            }
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(HankeError("HAI1001", "Hanke $hankeId not found"))
        } catch (e: Exception) {
            logger.error(e) {
                "HAI1012 - Internal error while saving Hanke $hankeId geometry"
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(HankeError("HAI1012", "Internal error while saving Hanke $hankeId geometry"))
        }
        return ResponseEntity.noContent().build()
    }
}