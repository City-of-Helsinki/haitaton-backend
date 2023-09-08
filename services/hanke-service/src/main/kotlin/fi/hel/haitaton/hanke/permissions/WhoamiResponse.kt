package fi.hel.haitaton.hanke.permissions

data class WhoamiResponse(
    val userId: String,
    val kayttooikeustaso: Kayttooikeustaso,
    val kayttooikeudet: List<PermissionCode>
) {
    constructor(
        permissionEntity: PermissionEntity
    ) : this(
        permissionEntity.userId,
        permissionEntity.kayttooikeustaso,
        permissionEntity.kayttooikeustasoEntity.permissionCodes
    )
}
