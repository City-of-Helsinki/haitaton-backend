package fi.hel.haitaton.hanke.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.hel.haitaton.hanke.ContactType
import fi.hel.haitaton.hanke.Haitta13
import fi.hel.haitaton.hanke.KaistajarjestelynPituus
import fi.hel.haitaton.hanke.TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.Yhteyshenkilo
import fi.hel.haitaton.hanke.geometria.Geometriat
import java.time.ZonedDateTime

interface BaseHanke : HasYhteystiedot {
    val nimi: String
    val vaihe: Vaihe?
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

interface Yhteystieto : HasId<Int> {
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
    val geometriat: Geometriat?
    val kaistaHaitta: TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin?
    val kaistaPituusHaitta: KaistajarjestelynPituus?
    val meluHaitta: Haitta13?
    val polyHaitta: Haitta13?
    val tarinaHaitta: Haitta13?
    val nimi: String?
}
