package fi.hel.haitaton.hanke.domain

import java.time.LocalDate

data class HankeSearch(
        var periodBegin: LocalDate?,
        var periodEnd: LocalDate?
)