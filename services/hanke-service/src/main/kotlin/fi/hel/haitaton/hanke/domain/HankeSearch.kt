package fi.hel.haitaton.hanke.domain

import fi.hel.haitaton.hanke.SaveType
import java.time.LocalDate

data class HankeSearch(
        var periodBegin: LocalDate? = null,
        var periodEnd: LocalDate? = null,
        val saveType: SaveType? = null
)