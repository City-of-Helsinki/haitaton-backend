package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Perustaja
import javax.persistence.Column
import javax.persistence.Embeddable

@Embeddable
data class PerustajaEntity(
    @Column(name = "perustajanimi") var nimi: String?,
    @Column(name = "perustajasahkoposti") var sahkoposti: String
)

fun PerustajaEntity.toDomainObject(): Perustaja = Perustaja(nimi, sahkoposti)
