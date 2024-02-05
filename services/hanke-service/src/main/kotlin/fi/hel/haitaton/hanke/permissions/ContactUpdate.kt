package fi.hel.haitaton.hanke.permissions

import jakarta.validation.constraints.NotBlank

data class ContactUpdate(@NotBlank val sahkoposti: String, @NotBlank val puhelinnumero: String)
