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
    fun extractYhteystiedot(): List<HankeYhteystieto>

    fun yhteystiedotByType(): Map<ContactType, List<HankeYhteystieto>>
}
