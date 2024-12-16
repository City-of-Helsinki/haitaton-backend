package fi.hel.haitaton.hanke.hakemus

import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import fi.hel.haitaton.hanke.domain.Haittojenhallintasuunnitelma
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
    @JsonSetter(nulls = Nulls.AS_EMPTY)
    val haittojenhallintasuunnitelma: Haittojenhallintasuunnitelma = mapOf(),
) : Hakemusalue {
    override fun geometries(): List<Polygon> = tyoalueet.map { it.geometry }

    fun withoutTormaystarkastelut() =
        copy(tyoalueet = tyoalueet.map { it.copy(tormaystarkasteluTulos = null) })

    /**
     * Returns a new [TormaystarkasteluTulos] that contains the combination of the highest indexes
     * from all the työalue in this area. Autoliikenne contains the autoliikenneluokittelu with the
     * highest index.
     */
    fun worstCasesInTormaystarkastelut(): TormaystarkasteluTulos? {
        val tulokset = tyoalueet.mapNotNull { it.tormaystarkasteluTulos }.ifEmpty { null }

        return tulokset?.let {
            TormaystarkasteluTulos(
                autoliikenne = tulokset.map { it.autoliikenne }.maxBy { it.indeksi },
                pyoraliikenneindeksi = tulokset.maxOf { it.pyoraliikenneindeksi },
                linjaautoliikenneindeksi = tulokset.maxOf { it.linjaautoliikenneindeksi },
                raitioliikenneindeksi = tulokset.maxOf { it.raitioliikenneindeksi },
            )
        }
    }
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
