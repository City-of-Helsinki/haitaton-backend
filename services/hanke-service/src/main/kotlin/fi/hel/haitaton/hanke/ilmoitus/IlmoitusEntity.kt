package fi.hel.haitaton.hanke.ilmoitus

import fi.hel.haitaton.hanke.hakemus.HakemusEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "ilmoitus")
class IlmoitusEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING) val type: IlmoitusType,
    @Column(name = "date_reported") val dateReported: LocalDate,
    @Column(name = "created_at") val createdAt: OffsetDateTime = OffsetDateTime.now(),
    @ManyToOne(fetch = FetchType.EAGER, cascade = [CascadeType.ALL])
    @JoinColumn(name = "application_id")
    var hakemus: HakemusEntity,
) {
    fun toDomain() = Ilmoitus(id, type, dateReported, createdAt)
}
