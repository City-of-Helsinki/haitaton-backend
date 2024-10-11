package fi.hel.haitaton.hanke.taydennys

import fi.hel.haitaton.hanke.permissions.HankekayttajaEntity
import java.util.UUID

interface YhteyshenkiloEntity {
    val id: UUID
    var hankekayttaja: HankekayttajaEntity
    var tilaaja: Boolean
}
