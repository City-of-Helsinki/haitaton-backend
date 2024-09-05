package fi.hel.haitaton.hanke.valmistumisilmoitus

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

/** Operational condition and work finished reports sent by the user. */
@Entity
@Table(name = "valmistumisilmoitus")
class ValmistumisilmoitusEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Enumerated(EnumType.STRING) val type: ValmistumisilmoitusType,
    val hakemustunnus: String,
    @Column(name = "date_reported") val dateReported: LocalDate,
    @Column(name = "created_at") val createdAt: OffsetDateTime = OffsetDateTime.now(),
    @ManyToOne(fetch = FetchType.EAGER, cascade = [CascadeType.ALL])
    @JoinColumn(name = "application_id")
    var hakemus: HakemusEntity,
) {
    fun toDomain() = Valmistumisilmoitus(id, type, hakemustunnus, dateReported, createdAt)
}
