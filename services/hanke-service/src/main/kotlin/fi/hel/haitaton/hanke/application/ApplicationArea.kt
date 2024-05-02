package fi.hel.haitaton.hanke.application

import fi.hel.haitaton.hanke.domain.TyomaaTyyppi
import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.tormaystarkastelu.Meluhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Polyhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Tarinahaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import fi.hel.haitaton.hanke.tormaystarkastelu.VaikutusAutoliikenteenKaistamaariin
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
    val meluhaitta: Meluhaitta,
    val polyhaitta: Polyhaitta,
    val tarinahaitta: Tarinahaitta,
    val kaistahaitta: VaikutusAutoliikenteenKaistamaariin,
    val kaistahaittojenPituus: AutoliikenteenKaistavaikutustenPituus,
    val lisatiedot: String,
) : ApplicationArea {
    override fun geometries(): List<Polygon> = tyoalueet.map { it.geometry }
}

data class Tyoalue(
    val geometry: Polygon,
    val area: Double,
    val tormaystarkasteluTulos: TormaystarkasteluTulos,
)
