package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.permissions.Permission
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionProfiles

object PermissionFactory : Factory<Permission>() {
    const val defaultId = 1

    /**
     * Creates a new permission with default values. The default values can be overridden with named
     * parameters.
     *
     * Examples:
     * ```
     * PermissionFactory.create()
     * PermissionFactory.create(hankeId = 12, userId = "testuser")
     * ```
     */
    fun create(
        id: Int = defaultId,
        userId: String = HankeFactory.defaultUser,
        hankeId: Int = HankeFactory.defaultId,
        permissions: List<PermissionCode> = PermissionProfiles.HANKE_OWNER_PERMISSIONS,
    ): Permission = Permission(id, userId, hankeId, permissions)
}
