package fi.hel.haitaton.hanke.permissions

import jakarta.validation.constraints.NotBlank

data class ContactUpdate(
    @field:NotBlank val sahkoposti: String,
    @field:NotBlank val puhelinnumero: String
)
