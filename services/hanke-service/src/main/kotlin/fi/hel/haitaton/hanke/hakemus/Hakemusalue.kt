package fi.hel.haitaton.hanke.hakemus

import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import fi.hel.haitaton.hanke.domain.Haittojenhallintasuunnitelma
import fi.hel.haitaton.hanke.domain.Haittojenhallintatyyppi
import fi.hel.haitaton.hanke.domain.TyomaaTyyppi
import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.tormaystarkastelu.Meluhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Polyhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Tarinahaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import fi.hel.haitaton.hanke.tormaystarkastelu.VaikutusAutoliikenteenKaistamaariin
import kotlin.reflect.KProperty1
import org.geojson.Polygon

sealed interface Hakemusalue {
    val name: String

    fun geometries(): List<Polygon>

    fun listChanges(index: Int, other: Hakemusalue?): List<String>? =
        if (this != other) listOf("areas[$index]") else null
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

    override fun listChanges(index: Int, other: Hakemusalue?): List<String>? {
        if (other == null) return listOf("areas[$index]")
        other as KaivuilmoitusAlue
        val changes = mutableListOf<String>()
        changes.addAll(checkChangesInTyoalueet(index, tyoalueet, other.tyoalueet))
        checkChange(index, KaivuilmoitusAlue::name, other)?.let { changes.add(it) }
        checkChange(index, KaivuilmoitusAlue::katuosoite, other)?.let { changes.add(it) }
        checkChange(index, KaivuilmoitusAlue::tyonTarkoitukset, other)?.let { changes.add(it) }
        checkChange(index, KaivuilmoitusAlue::meluhaitta, other)?.let { changes.add(it) }
        checkChange(index, KaivuilmoitusAlue::polyhaitta, other)?.let { changes.add(it) }
        checkChange(index, KaivuilmoitusAlue::tarinahaitta, other)?.let { changes.add(it) }
        checkChange(index, KaivuilmoitusAlue::kaistahaitta, other)?.let { changes.add(it) }
        checkChange(index, KaivuilmoitusAlue::kaistahaittojenPituus, other)?.let { changes.add(it) }
        checkChange(index, KaivuilmoitusAlue::lisatiedot, other)?.let { changes.add(it) }
        // haittojenhallintasuunnitelma changes do not affect the area itself, therefore we add the
        // root area here if there are changes in other fields.
        if (changes.isNotEmpty()) {
            changes.add(0, "areas[$index]")
        }
        changes.addAll(
            checkChangesInHaittojenhallintasuunnitelma(
                index,
                haittojenhallintasuunnitelma,
                other.haittojenhallintasuunnitelma,
            )
        )
        return if (changes.isNotEmpty()) changes else null
    }
}

data class Tyoalue(
    val geometry: Polygon,
    val area: Double,
    val tormaystarkasteluTulos: TormaystarkasteluTulos?,
) {
    fun listChanges(alueIndex: Int, tyoalueIndex: Int, other: Tyoalue?): List<String>? {
        if (other == null) return listOf("areas[$alueIndex].tyoalueet[$tyoalueIndex]")
        val changes = mutableListOf<String>()
        checkChange(alueIndex, tyoalueIndex, Tyoalue::geometry, other)?.let { changes.add(it) }
        return if (changes.isNotEmpty())
            listOf("areas[$alueIndex].tyoalueet[$tyoalueIndex]") + changes
        else null
    }
}

fun List<KaivuilmoitusAlue>?.combinedAddress(): PostalAddress? =
    this?.map { it.katuosoite }
        ?.toSet()
        ?.joinToString(", ")
        ?.let { StreetAddress(it) }
        ?.let { PostalAddress(it, "", "") }

private fun KaivuilmoitusAlue.checkChange(
    index: Int,
    property: KProperty1<KaivuilmoitusAlue, Any?>,
    second: KaivuilmoitusAlue,
): String? =
    if (property.get(this) != property.get(second)) {
        "areas[$index].${property.name}"
    } else null

private fun Tyoalue.checkChange(
    alueIndex: Int,
    tyoalueIndex: Int,
    property: KProperty1<Tyoalue, Any?>,
    second: Tyoalue,
): String? =
    if (property.get(this) != property.get(second)) {
        "areas[$alueIndex].tyoalueet[$tyoalueIndex].${property.name}"
    } else null

private fun checkChangesInTyoalueet(
    index: Int,
    firstAreas: List<Tyoalue>,
    secondAreas: List<Tyoalue>,
): List<String> {
    val changedElementsInFirst =
        firstAreas.withIndex().mapNotNull { (i, tyoalue) ->
            tyoalue.listChanges(index, i, secondAreas.getOrNull(i))
        }
    val elementsInSecondButNotFirst =
        secondAreas.indices.drop(firstAreas.size).map { "areas[$index].tyoalueet[$it]" }
    return (changedElementsInFirst.flatten() + elementsInSecondButNotFirst)
}

private fun checkChangesInHaittojenhallintasuunnitelma(
    index: Int,
    first: Haittojenhallintasuunnitelma,
    second: Haittojenhallintasuunnitelma,
): List<String> =
    Haittojenhallintatyyppi.entries.mapNotNull { tyyppi ->
        if (first[tyyppi] != second[tyyppi]) {
            "areas[$index].haittojenhallintasuunnitelma[${tyyppi.name}]"
        } else null
    }
