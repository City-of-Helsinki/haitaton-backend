package fi.hel.haitaton.hanke.domain

import fi.hel.haitaton.hanke.hakemus.ApplicationContactType
import fi.hel.haitaton.hanke.hakemus.YhteyshenkiloEntity
import fi.hel.haitaton.hanke.hakemus.YhteystietoEntity
import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity

interface HasYhteystietoEntities<H : YhteyshenkiloEntity> {
    val yhteystiedot: Map<ApplicationContactType, YhteystietoEntity<H>>

    /** Returns all distinct contact users. */
    fun allContactUsers(): List<HankekayttajaEntity> =
        yhteystiedot.values
            .flatMap { it.yhteyshenkilot }
            .map { it.hankekayttaja }
            .distinctBy { it.id }
}
