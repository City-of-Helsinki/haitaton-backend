package fi.hel.haitaton.hanke.permissions

import java.util.UUID

data class WhoamiResponse(
    val hankeKayttajaId: UUID?,
    val kayttooikeustaso: Kayttooikeustaso,
    val kayttooikeudet: List<PermissionCode>
) {
    constructor(
        hankeKayttajaId: UUID?,
        kayttooikeustasoEntity: KayttooikeustasoEntity
    ) : this(
        hankeKayttajaId,
        kayttooikeustasoEntity.kayttooikeustaso,
        kayttooikeustasoEntity.permissionCodes
    )
}
