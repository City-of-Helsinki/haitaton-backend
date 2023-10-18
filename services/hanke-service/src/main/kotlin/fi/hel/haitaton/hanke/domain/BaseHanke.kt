package fi.hel.haitaton.hanke.domain

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.hel.haitaton.hanke.ContactType
import fi.hel.haitaton.hanke.SuunnitteluVaihe
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.Yhteyshenkilo

interface BaseHanke : HasYhteystiedot {
    val nimi: String
    val vaihe: Vaihe?
    val suunnitteluVaihe: SuunnitteluVaihe?
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
     */
    @JsonIgnore
    fun isAnyFieldSet(): Boolean =
        isAnyMandatoryFieldSet() || !organisaatioNimi.isNullOrBlank() || !osasto.isNullOrBlank()

    /**
     * Returns true if at least one mandatory Yhteystieto-field is non-null, non-empty and
     * non-whitespace-only.
     */
    @JsonIgnore fun isAnyMandatoryFieldSet(): Boolean = nimi.isNotBlank() || email.isNotBlank()
}
