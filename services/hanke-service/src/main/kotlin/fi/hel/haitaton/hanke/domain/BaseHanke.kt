package fi.hel.haitaton.hanke.domain

import fi.hel.haitaton.hanke.ContactType
import fi.hel.haitaton.hanke.SuunnitteluVaihe
import fi.hel.haitaton.hanke.Vaihe

interface BaseHanke : HasYhteystiedot {
    val nimi: String
    val vaihe: Vaihe?
    val suunnitteluVaihe: SuunnitteluVaihe?
    val alueet: List<Hankealue>?
    val tyomaaKatuosoite: String?
}

interface HasYhteystiedot {
    val omistajat: List<HankeYhteystieto>?
    val rakennuttajat: List<HankeYhteystieto>?
    val toteuttajat: List<HankeYhteystieto>?
    val muut: List<HankeYhteystieto>?

    fun extractYhteystiedot(): List<HankeYhteystieto> =
        listOfNotNull(omistajat, rakennuttajat, toteuttajat, muut).flatten()

    fun yhteystiedotByType(): Map<ContactType, List<HankeYhteystieto>> =
        mapOf(
            ContactType.OMISTAJA to (omistajat ?: listOf()),
            ContactType.RAKENNUTTAJA to (rakennuttajat ?: listOf()),
            ContactType.TOTEUTTAJA to (toteuttajat ?: listOf()),
            ContactType.MUU to (muut ?: listOf()),
        )
}
