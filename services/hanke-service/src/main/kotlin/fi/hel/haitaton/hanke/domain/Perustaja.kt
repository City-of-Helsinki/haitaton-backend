package fi.hel.haitaton.hanke.domain

import fi.hel.haitaton.hanke.PerustajaEntity
import fi.hel.haitaton.hanke.permissions.UserContact
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Founder information")
data class Perustaja(
    @field:Schema(description = "Name") val nimi: String,
    @field:Schema(description = "Email address") val email: String
) {
    fun toEntity(): PerustajaEntity = PerustajaEntity(nimi, email)

    fun toUserContact(): UserContact = UserContact(nimi, email)
}
