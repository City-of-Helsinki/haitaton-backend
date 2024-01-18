package fi.hel.haitaton.hanke.domain

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(description = "Contact person")
data class Yhteyshenkilo(
    @field:Schema(description = "Id of the HankeKayttaja") val id: UUID,
    @field:Schema(description = "First name") val etunimi: String,
    @field:Schema(description = "Last name") val sukunimi: String,
    @field:Schema(description = "Email address") val email: String,
    @field:Schema(description = "Phone number") val puhelinnumero: String,
)
