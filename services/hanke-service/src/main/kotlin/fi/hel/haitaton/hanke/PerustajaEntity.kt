package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Perustaja
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
data class PerustajaEntity(
    @Column(name = "perustajanimi") var nimi: String?,
    @Column(name = "perustajaemail") var email: String
) {
    fun toDomainObject(): Perustaja = Perustaja(nimi, email)
}
