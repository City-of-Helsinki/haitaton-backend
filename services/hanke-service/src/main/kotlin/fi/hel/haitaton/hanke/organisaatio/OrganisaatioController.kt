package fi.hel.haitaton.hanke.organisaatio

import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

private val logger = KotlinLogging.logger { }

@Component
@RestController
@RequestMapping("/organisaatiot")
@Validated
class OrganisaatioController {

    @GetMapping
    fun getOrganisaatiot(): ResponseEntity<Any> {
        val organisaatioList = """
            [
              {
                "id": 1,
                "nimi": "DNA Oyj",
                "tunnus": "DNA"
              },
              {
                "id": 2,
                "nimi": "DNA Welho Oy",
                "tunnus": "WELHO"
              },
              {
                "id": 3,
                "nimi": "Elisa Oyj",
                "tunnus": "ELISA"
              },
              {
                "id": 4,
                "nimi": "FremantleMedia Finland Oy",
                "tunnus": "FREM"
              },
              {
                "id": 5,
                "nimi": "Helen Oy",
                "tunnus": "HELEN"
              },
              {
                "id": 6,
                "nimi": "Helen sähköverkko Oy",
                "tunnus": "HELENSAH"
              },
              {
                "id": 7,
                "nimi": "Helsingin kaupunki KYMP",
                "tunnus": "HELKYMP"
              },
              {
                "id": 8,
                "nimi": "Helsingin tapahtumasäätiö sr",
                "tunnus": "HELTA"
              },
              {
                "id": 9,
                "nimi": "Helsingin seudun ympäristöpalvelut -kuntayhtymä HSY",
                "tunnus": "HSY"
              },
              {
                "id": 10,
                "nimi": "Niemi palvelut Oy",
                "tunnus": "NIEMI"
              },
              {
                "id": 11,
                "nimi": "Raide-Jokeri Allianssi / KYMP/MAKA/LIKE",
                "tunnus": "RAIDE"
              },
              {
                "id": 12,
                "nimi": "Suomen Ilotulitus Oy",
                "tunnus": "SUOILO"
              },
              {
                "id": 13,
                "nimi": "Telia Finland Oyj",
                "tunnus": "TELIA"
              },
              {
                "id": 14,
                "nimi": "Varmatie Oy",
                "tunnus": "VARMATIE"
              },
              {
                "id": 15,
                "nimi": "Viktor Ek Muutot Oy",
                "tunnus": "EK"
              },
              {
                "id": 16,
                "nimi": "Yellow Film & TV Oy",
                "tunnus": "YELLOW"
              },
              {
                "id": 17,
                "nimi": "YIT Suomi Oy",
                "tunnus": "YIT"
              },
              {
                "id": 18,
                "nimi": "Yleisradio Oy",
                "tunnus": "YLE"
              },
              {
                "id": 19,
                "nimi": "Zodiak Finland Oy",
                "tunnus": "ZODIAK"
              }
            ]
        """.trimIndent()

        return ResponseEntity.status(HttpStatus.OK).body(organisaatioList)
    }
}
