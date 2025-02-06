package fi.hel.haitaton.hanke.pdf

import fi.hel.haitaton.hanke.domain.SavedHankealue
import fi.hel.haitaton.hanke.getResourceAsBytes
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusAlue
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("test")
class MockMapGenerator : MapGenerator {
    private val imageBytes = "/fi/hel/haitaton/hanke/pdf-test-data/blank.png".getResourceAsBytes()

    override fun mapWithAreas(
        areas: List<KaivuilmoitusAlue>,
        hankealueet: List<SavedHankealue>,
        imageWidth: Int,
        imageHeight: Int,
        getIndex: (TormaystarkasteluTulos?) -> Float?,
    ): ByteArray = imageBytes
}
