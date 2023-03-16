package fi.hel.haitaton.hanke.domain

import fi.hel.haitaton.hanke.PerustajaEntity

data class Perustaja(val nimi: String?, val sahkoposti: String)

fun Perustaja.toEntity(): PerustajaEntity = PerustajaEntity(nimi, sahkoposti)
