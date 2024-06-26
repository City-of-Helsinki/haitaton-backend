package fi.hel.haitaton.hanke.permissions

import jakarta.validation.constraints.NotBlank

data class KayttajaUpdate(
    @field:NotBlank val sahkoposti: String,
    @field:NotBlank val puhelinnumero: String,
    val etunimi: String? = null,
    val sukunimi: String? = null
)
