package fi.hel.haitaton.hanke.permissions

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

data class PermissionUpdate(
    @field:Schema(description = "Permissions to update.") val kayttajat: List<PermissionDto>
)

data class PermissionDto(
    @field:Schema(description = "HankeKayttaja ID") val id: UUID,
    @field:Schema(description = "New access level in Hanke") val kayttooikeustaso: Kayttooikeustaso,
)

data class NewUserRequest(
    @field:Schema(
        description = "The first name of the user",
    )
    val etunimi: String,
    @field:Schema(
        description = "The last name of the user",
    )
    val sukunimi: String,
    @field:Schema(
        description = "The email address of the user. The invitation will be sent to this address.",
    )
    val sahkoposti: String,
    @field:Schema(
        description = "The phone number of the user.",
    )
    val puhelinnumero: String,
) {
    fun toHankekayttajaInput(): HankekayttajaInput =
        HankekayttajaInput(etunimi, sukunimi, sahkoposti, puhelinnumero)
}
