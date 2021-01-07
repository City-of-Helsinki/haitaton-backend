package fi.hel.haitaton.hanke.domain

import java.time.LocalDate
import java.time.ZonedDateTime

data class HankeSearch (
        var nimi: String?) {
    var periodBegin: String? = null
    var periodEnd: String? = null
}