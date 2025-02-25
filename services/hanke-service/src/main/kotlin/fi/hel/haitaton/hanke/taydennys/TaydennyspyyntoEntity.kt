package fi.hel.haitaton.hanke.taydennys

import fi.hel.haitaton.hanke.allu.InformationRequestFieldKey
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapKeyColumn
import jakarta.persistence.MapKeyEnumerated
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "taydennyspyynto")
class TaydennyspyyntoEntity(
    @Id val id: UUID = UUID.randomUUID(),
    @Column(name = "application_id") val applicationId: Long,
    @Column(name = "allu_id") val alluId: Int,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "taydennyspyynnon_kentta",
        joinColumns = [JoinColumn(name = "taydennyspyynto_id", referencedColumnName = "id")],
    )
    @MapKeyColumn(name = "key")
    @Column(name = "description")
    @MapKeyEnumerated(EnumType.STRING)
    val kentat: MutableMap<InformationRequestFieldKey, String> = mutableMapOf(),
) {
    fun toDomain(): Taydennyspyynto = Taydennyspyynto(id, applicationId, kentat.toMap())
}
