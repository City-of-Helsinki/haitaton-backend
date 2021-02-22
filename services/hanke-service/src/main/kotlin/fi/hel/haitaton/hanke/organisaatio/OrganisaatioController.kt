package fi.hel.haitaton.hanke.organisaatio

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger { }

@RestController
@RequestMapping("/organisaatiot")
@Validated
class OrganisaatioController(@Autowired private val service: OrganisaatioService) {

    @GetMapping
    fun getOrganisaatiot(): ResponseEntity<Any> {
        logger.info {
            "Getting organisaatiot..."
        }
        val organisaatiot = service.getOrganisaatiot()
        logger.info { "Got organisaatiot." }
        return ResponseEntity.status(HttpStatus.OK).body(organisaatiot)
    }
}
