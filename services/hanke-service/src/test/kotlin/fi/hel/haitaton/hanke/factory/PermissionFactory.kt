package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.permissions.Permission

object PermissionFactory {

    const val PERMISSION_ID = 65110
    const val USER_ID = "permissionUser"
    const val HANKE_ID = 984141
    val KAYTTOOIKEUSTASO = Kayttooikeustaso.KATSELUOIKEUS

    fun create(
        id: Int = PERMISSION_ID,
        userId: String = USER_ID,
        hankeId: Int = HANKE_ID,
        kayttooikeustaso: Kayttooikeustaso = KAYTTOOIKEUSTASO,
    ) = Permission(id, userId, hankeId, kayttooikeustaso)
}
