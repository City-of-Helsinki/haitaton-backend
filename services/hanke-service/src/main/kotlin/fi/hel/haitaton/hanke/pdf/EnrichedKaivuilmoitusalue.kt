package fi.hel.haitaton.hanke.pdf

import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusAlue

data class EnrichedKaivuilmoitusalue(
    val totalArea: Float?,
    val hankealueName: String,
    val alue: KaivuilmoitusAlue
)
