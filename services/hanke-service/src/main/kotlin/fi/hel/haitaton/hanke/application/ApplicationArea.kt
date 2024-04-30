package fi.hel.haitaton.hanke.application

import fi.hel.haitaton.hanke.Haitat
import fi.hel.haitaton.hanke.domain.TyomaaTyyppi
import org.geojson.Polygon

sealed interface ApplicationArea {
    val name: String
    val geometry: Polygon
}

data class CableReportApplicationArea(override val name: String, override val geometry: Polygon) :
    ApplicationArea

data class ExcavationNotificationArea(
    override val name: String,
    override val geometry: Polygon,
    val hankealueId: Int,
    val tyoalueet: List<Tyoalue>,
    val katuosoite: String,
    val tyonTarkoitukset: Set<TyomaaTyyppi>,
    val lisatiedot: String,
    val haitat: Haitat,
) : ApplicationArea

data class Tyoalue(
    val geometry: Polygon,
    val area: Double,
    val haittaindeksi: Float,
)
