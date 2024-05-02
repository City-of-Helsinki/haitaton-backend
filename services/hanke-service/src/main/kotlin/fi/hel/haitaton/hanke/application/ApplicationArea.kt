package fi.hel.haitaton.hanke.application

import fi.hel.haitaton.hanke.Haitat
import fi.hel.haitaton.hanke.domain.TyomaaTyyppi
import org.geojson.Polygon

sealed interface ApplicationArea {
    val name: String

    fun geometries(): List<Polygon>
}

data class CableReportApplicationArea(override val name: String, val geometry: Polygon) :
    ApplicationArea {
    override fun geometries(): List<Polygon> = listOf(geometry)
}

data class ExcavationNotificationArea(
    override val name: String,
    val hankealueId: Int,
    val tyoalueet: List<Tyoalue>,
    val katuosoite: String,
    val tyonTarkoitukset: Set<TyomaaTyyppi>,
    val lisatiedot: String,
    val haitat: Haitat,
) : ApplicationArea {
    override fun geometries(): List<Polygon> = tyoalueet.map { it.geometry }
}

data class Tyoalue(
    val geometry: Polygon,
    val area: Double,
    val haittaindeksi: Float,
)
