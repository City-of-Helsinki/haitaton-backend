package fi.hel.haitaton.hanke.hakemus

import fi.hel.haitaton.hanke.domain.TyomaaTyyppi
import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.tormaystarkastelu.Meluhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Polyhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Tarinahaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import fi.hel.haitaton.hanke.tormaystarkastelu.VaikutusAutoliikenteenKaistamaariin
import org.geojson.Polygon

sealed interface Hakemusalue {
    val name: String

    fun geometries(): List<Polygon>
}

data class JohtoselvitysHakemusalue(override val name: String, val geometry: Polygon) :
    Hakemusalue {
    override fun geometries(): List<Polygon> = listOf(geometry)
}

data class KaivuilmoitusAlue(
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
    val lisatiedot: String?,
) : Hakemusalue {
    override fun geometries(): List<Polygon> = tyoalueet.map { it.geometry }
}

data class Tyoalue(
    val geometry: Polygon,
    val area: Double,
    val tormaystarkasteluTulos: TormaystarkasteluTulos?,
)

fun List<KaivuilmoitusAlue>?.combinedAddress(): PostalAddress? =
    this?.map { it.katuosoite }
        ?.toSet()
        ?.joinToString(", ")
        ?.let { StreetAddress(it) }
        ?.let { PostalAddress(it, "", "") }
