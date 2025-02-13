package fi.hel.haitaton.hanke.muutosilmoitus

import fi.hel.haitaton.hanke.hakemus.ApplicationContactType
import fi.hel.haitaton.hanke.hakemus.HakemusEntityData
import io.hypersistence.utils.hibernate.type.json.JsonType
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.MapKey
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import java.time.OffsetDateTime
import java.util.UUID
import org.hibernate.annotations.BatchSize
import org.hibernate.annotations.Type

@Entity
@Table(name = "muutosilmoitus")
class MuutosilmoitusEntity(
    @Id var id: UUID = UUID.randomUUID(),
    @Column(name = "application_id") val applicationId: Long,
    @Column(name = "sent") var sent: OffsetDateTime?,
    @Type(JsonType::class)
    @Column(columnDefinition = "jsonb", name = "application_data")
    var hakemusData: HakemusEntityData,
    @OneToMany(
        fetch = FetchType.LAZY,
        mappedBy = "muutosilmoitus",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    @MapKey(name = "rooli")
    @BatchSize(size = 100)
    var yhteystiedot: MutableMap<ApplicationContactType, MuutosilmoituksenYhteystietoEntity> =
        mutableMapOf(),
)
