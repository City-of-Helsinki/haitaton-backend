package fi.hel.haitaton.hanke.permissions

data class WhoamiResponse(val userId: String, val kayttooikeustaso: Kayttooikeustaso) {
    constructor(
        permissionEntity: PermissionEntity
    ) : this(permissionEntity.userId, permissionEntity.kayttooikeustaso.kayttooikeustaso)
}
