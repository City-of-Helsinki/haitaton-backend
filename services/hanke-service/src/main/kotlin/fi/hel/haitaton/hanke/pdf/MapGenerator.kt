package fi.hel.haitaton.hanke.pdf

import fi.hel.haitaton.hanke.domain.SavedHankealue
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusAlue
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos

interface MapGenerator {
    fun mapWithAreas(
        areas: List<KaivuilmoitusAlue>,
        hankealueet: List<SavedHankealue>,
        imageWidth: Int,
        imageHeight: Int,
        getIndex: (TormaystarkasteluTulos?) -> Float?,
    ): ByteArray
}
