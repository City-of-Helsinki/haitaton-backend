package fi.hel.haitaton.hanke.hakemus

import com.fasterxml.jackson.annotation.JsonSetter
import com.fasterxml.jackson.annotation.Nulls
import fi.hel.haitaton.hanke.checkChange
import fi.hel.haitaton.hanke.domain.Haittojenhallintasuunnitelma
import fi.hel.haitaton.hanke.domain.Haittojenhallintatyyppi
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

    fun listChanges(path: String, other: Hakemusalue?): List<String> =
        if (this != other) listOf(path) else emptyList()
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

    override fun listChanges(path: String, other: Hakemusalue?): List<String> {
        if (other == null) return listOf(path)
        other as KaivuilmoitusAlue
        val changes = mutableListOf<String>()
        changes.addAll(checkChangesInTyoalueet(path, tyoalueet, other.tyoalueet))
        checkChange(KaivuilmoitusAlue::name, other)?.let { changes.add("$path.$it") }
        checkChange(KaivuilmoitusAlue::katuosoite, other)?.let { changes.add("$path.$it") }
        checkChange(KaivuilmoitusAlue::tyonTarkoitukset, other)?.let { changes.add("$path.$it") }
        checkChange(KaivuilmoitusAlue::meluhaitta, other)?.let { changes.add("$path.$it") }
        checkChange(KaivuilmoitusAlue::polyhaitta, other)?.let { changes.add("$path.$it") }
        checkChange(KaivuilmoitusAlue::tarinahaitta, other)?.let { changes.add("$path.$it") }
        checkChange(KaivuilmoitusAlue::kaistahaitta, other)?.let { changes.add("$path.$it") }
        checkChange(KaivuilmoitusAlue::kaistahaittojenPituus, other)?.let {
            changes.add("$path.$it")
        }
        checkChange(KaivuilmoitusAlue::lisatiedot, other)?.let { changes.add("$path.$it") }
        // haittojenhallintasuunnitelma changes do not affect the area itself, therefore we add the
        // root area here if there are changes in other fields.
        if (changes.isNotEmpty()) {
            changes.add(0, path)
        }
        changes.addAll(
            checkChangesInHaittojenhallintasuunnitelma(
                path,
                haittojenhallintasuunnitelma,
                other.haittojenhallintasuunnitelma,
            )
        )
        return changes
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

private fun checkChangesInTyoalueet(
    path: String,
    firstAreas: List<Tyoalue>,
    secondAreas: List<Tyoalue>,
): List<String> {
    val workAreaPath = "$path.tyoalueet"
    val changedElementsInFirst =
        firstAreas.withIndex().flatMap { (i, tyoalue) ->
            val otherTyoalue = secondAreas.getOrNull(i)
            if (otherTyoalue == null) {
                listOf("$workAreaPath[$i]")
            } else if (tyoalue.geometry != otherTyoalue.geometry) {
                listOf("$workAreaPath[$i]", "$workAreaPath[$i].geometry")
            } else emptyList()
        }
    val elementsInSecondButNotFirst =
        secondAreas.indices.drop(firstAreas.size).map { "$workAreaPath[$it]" }
    return (changedElementsInFirst + elementsInSecondButNotFirst)
}

private fun checkChangesInHaittojenhallintasuunnitelma(
    path: String,
    first: Haittojenhallintasuunnitelma,
    second: Haittojenhallintasuunnitelma,
): List<String> =
    Haittojenhallintatyyppi.entries
        .filter { tyyppi -> first[tyyppi] != second[tyyppi] }
        .map { tyyppi -> "$path.haittojenhallintasuunnitelma[${tyyppi.name}]" }
