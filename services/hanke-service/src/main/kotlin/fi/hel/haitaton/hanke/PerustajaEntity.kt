package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.domain.Perustaja
import javax.persistence.Column
import javax.persistence.Embeddable

@Embeddable
class PerustajaEntity(
    @Column(name = "perustaja_nimi") var nimi: String?,
    @Column(name = "perustaja_sahkoposti") var sahkoposti: String
)

fun PerustajaEntity.toDomainObject(): Perustaja = Perustaja(nimi, sahkoposti)
