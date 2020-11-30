package fi.hel.haitaton.hanke.organisaatio

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger { }

@RestController
@RequestMapping("/organisaatiot")
@Validated
class OrganisaatioController(@Autowired private val service: OrganisaatioService) {

    @GetMapping
    fun getOrganisaatiot(): ResponseEntity<Any> {
        val organisaatiot = service.getOrganisaatiot()
        logger.info { organisaatiot }
        return ResponseEntity.status(HttpStatus.OK).body(organisaatiot)
    }
}
