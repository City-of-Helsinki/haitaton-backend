package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.PublicHanke
import fi.hel.haitaton.hanke.domain.hankeToPublic
import fi.hel.haitaton.hanke.validation.CompleteHankeValidator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/public-hankkeet")
class PublicHankeController(
    @Autowired private val hankeService: HankeService,
) {
    @GetMapping
    fun getAll(): List<PublicHanke> {
        val hankkeet =
            hankeService.loadAllHanke().filter { CompleteHankeValidator.isHankeComplete(it) }
        return hankkeet.map { hankeToPublic(it) }
    }
}
