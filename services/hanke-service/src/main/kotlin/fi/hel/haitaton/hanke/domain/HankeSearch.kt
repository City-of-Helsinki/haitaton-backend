package fi.hel.haitaton.hanke.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.hel.haitaton.hanke.SaveType
import java.time.LocalDate

data class HankeSearch(
    var periodBegin: LocalDate? = null,
    var periodEnd: LocalDate? = null,
    val saveType: SaveType? = null,
    var geometry: Boolean? = null
) {

    @JsonIgnore
    fun isEmpty(): Boolean {
        return (periodBegin == null || periodEnd == null) && saveType == null
    }

    fun includeGeometry(): Boolean {
        return geometry != null && geometry == true
    }
}
