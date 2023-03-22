package fi.hel.haitaton.hanke.domain

import fi.hel.haitaton.hanke.PerustajaEntity

data class Perustaja(val nimi: String?, val email: String)

fun Perustaja.toEntity(): PerustajaEntity = PerustajaEntity(nimi, email)
