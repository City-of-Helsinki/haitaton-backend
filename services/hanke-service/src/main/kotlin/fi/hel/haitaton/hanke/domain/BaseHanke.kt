package fi.hel.haitaton.hanke.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.hel.haitaton.hanke.ContactType
import fi.hel.haitaton.hanke.Yhteyshenkilo
import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.tormaystarkastelu.Meluhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Polyhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Tarinahaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.VaikutusAutoliikenteenKaistamaariin
import java.time.ZonedDateTime
import org.geojson.FeatureCollection

interface BaseHanke : HasYhteystiedot {
    val nimi: String
    val vaihe: Hankevaihe?
    val alueet: List<Hankealue>?
    val tyomaaKatuosoite: String?
}

interface HasYhteystiedot {
    val omistajat: List<Yhteystieto>?
    val rakennuttajat: List<Yhteystieto>?
    val toteuttajat: List<Yhteystieto>?
    val muut: List<Yhteystieto>?

    fun extractYhteystiedot(): List<Yhteystieto> =
        listOfNotNull(omistajat, rakennuttajat, toteuttajat, muut).flatten()

    fun yhteystiedotByType(): Map<ContactType, List<Yhteystieto>> =
        mapOf(
                ContactType.OMISTAJA to omistajat,
                ContactType.RAKENNUTTAJA to rakennuttajat,
                ContactType.TOTEUTTAJA to toteuttajat,
                ContactType.MUU to muut,
            )
            .mapValues { (_, yhteystiedot) -> yhteystiedot ?: listOf() }
}

interface Yhteystieto : HasId<Int?> {
    override val id: Int?
    val nimi: String
    val email: String
    val alikontaktit: List<Yhteyshenkilo>
    val puhelinnumero: String?
    val organisaatioNimi: String?
    val osasto: String?
    val rooli: String?
    val tyyppi: YhteystietoTyyppi?
    val ytunnus: String?

    /**
     * Returns true if at least one Yhteystieto-field is non-null, non-empty and
     * non-whitespace-only.
     *
     * Id and tyyppi are not considered concrete information by themselves, so they are ignored
     * here.
     *
     * Contact people are handled separately, so they are not included.
     */
    @JsonIgnore
    fun isAnyFieldSet(): Boolean =
        isAnyMandatoryFieldSet() ||
            !puhelinnumero.isNullOrBlank() ||
            !organisaatioNimi.isNullOrBlank() ||
            !osasto.isNullOrBlank() ||
            !rooli.isNullOrBlank() ||
            !ytunnus.isNullOrBlank()

    /**
     * Returns true if at least one mandatory Yhteystieto-field is non-null, non-empty and
     * non-whitespace-only.
     */
    @JsonIgnore fun isAnyMandatoryFieldSet(): Boolean = nimi.isNotBlank() || email.isNotBlank()
}

interface Hankealue {
    val haittaAlkuPvm: ZonedDateTime?
    val haittaLoppuPvm: ZonedDateTime?
    val geometriat: HasFeatures?
    val kaistaHaitta: VaikutusAutoliikenteenKaistamaariin?
    val kaistaPituusHaitta: AutoliikenteenKaistavaikutustenPituus?
    val meluHaitta: Meluhaitta?
    val polyHaitta: Polyhaitta?
    val tarinaHaitta: Tarinahaitta?
    val nimi: String
}

fun List<Hankealue>.geometriat(): List<HasFeatures> = mapNotNull { it.geometriat }

interface HasFeatures {
    val featureCollection: FeatureCollection?

    fun resetFeatureProperties(hankeTunnus: String?) {
        featureCollection?.let { collection ->
            collection.features.forEach { feature ->
                feature.properties = mutableMapOf<String, Any?>("hankeTunnus" to hankeTunnus)
            }
        }
    }

    fun hasFeatures(): Boolean {
        return !featureCollection?.features.isNullOrEmpty()
    }
}
