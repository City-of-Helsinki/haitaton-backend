package fi.hel.haitaton.hanke.application

import org.geojson.Polygon

sealed interface ApplicationArea {
    val name: String
    val geometry: Polygon
}

data class CableReportApplicationArea(override val name: String, override val geometry: Polygon) :
    ApplicationArea

data class ExcavationNotificationArea(override val name: String, override val geometry: Polygon) :
    ApplicationArea
