package fi.hel.haitaton.hanke.permissions

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

data class PermissionUpdate(
    @field:Schema(description = "Permissions to update.") val kayttajat: List<PermissionDto>
)

data class PermissionDto(
    @field:Schema(description = "HankeKayttaja ID") val id: UUID,
    @field:Schema(description = "New role in Hanke") val rooli: Role,
)
