package fi.hel.haitaton.hanke.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.LocalDate

data class HankeSearch(
    var periodBegin: LocalDate? = null,
    var periodEnd: LocalDate? = null,
    var geometry: Boolean? = null
) {

    @JsonIgnore
    fun isEmpty(): Boolean {
        return periodBegin == null || periodEnd == null
    }

    fun includeGeometry(): Boolean {
        return geometry != null && geometry == true
    }
}
