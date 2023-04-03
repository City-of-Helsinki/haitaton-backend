package fi.hel.haitaton.hanke.permissions

import fi.hel.haitaton.hanke.getCurrentTimeUTC
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.util.UUID
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.Id
import javax.persistence.OneToOne
import javax.persistence.Table
import kotlin.streams.asSequence
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Entity
@Table(name = "kayttaja_tunniste")
class KayttajaTunnisteEntity(
    @Id val id: UUID,
    val tunniste: String,
    @Column(name = "created_at") val createdAt: OffsetDateTime,
    @Column(name = "sent_at") val sentAt: OffsetDateTime?,
    @Enumerated(EnumType.STRING) val role: Role,
    @OneToOne(mappedBy = "kayttajaTunniste") val hankeKayttaja: HankeKayttajaEntity?
) {
    constructor() :
        this(
            id = UUID.randomUUID(),
            tunniste = randomToken(),
            createdAt = getCurrentTimeUTC().toOffsetDateTime(),
            sentAt = null,
            role = Role.KATSELUOIKEUS,
            hankeKayttaja = null
        )

    companion object {
        private const val tokenLength: Int = 24
        private val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        private val secureRandom: SecureRandom = SecureRandom.getInstanceStrong()

        fun randomToken(): String =
            secureRandom
                .ints(tokenLength.toLong(), 0, charPool.size)
                .asSequence()
                .map(charPool::get)
                .joinToString("")
    }
}

@Repository interface KayttajaTunnisteRepository : JpaRepository<KayttajaTunnisteEntity, UUID> {}
