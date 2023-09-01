package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.permissions.Permission
import fi.hel.haitaton.hanke.permissions.Role

object PermissionFactory {

    const val PERMISSION_ID = 65110
    const val USER_ID = "permissionUser"
    const val HANKE_ID = 984141
    val ROLE = Role.KATSELUOIKEUS

    fun create(
        id: Int = PERMISSION_ID,
        userId: String = USER_ID,
        hankeId: Int = HANKE_ID,
        role: Role = ROLE,
    ) = Permission(id, userId, hankeId, role)
}
