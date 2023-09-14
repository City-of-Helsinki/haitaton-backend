package fi.hel.haitaton.hanke.permissions

import com.fasterxml.jackson.annotation.JsonView
import fi.hel.haitaton.hanke.ChangeLogView
import fi.hel.haitaton.hanke.domain.HasId
import fi.hel.haitaton.hanke.getCurrentTimeUTC
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.streams.asSequence
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@JsonView(ChangeLogView::class)
data class KayttajaTunniste(
    override val id: UUID,
    val tunniste: String,
    val createdAt: OffsetDateTime,
    val sentAt: OffsetDateTime?,
    var kayttooikeustaso: Kayttooikeustaso,
    val hankeKayttajaId: UUID?
) : HasId<UUID>

@Entity
@Table(name = "kayttaja_tunniste")
class KayttajaTunnisteEntity(
    @Id val id: UUID = UUID.randomUUID(),
    val tunniste: String,
    @Column(name = "created_at") val createdAt: OffsetDateTime,
    @Column(name = "sent_at") val sentAt: OffsetDateTime?,
    @Enumerated(EnumType.STRING) var kayttooikeustaso: Kayttooikeustaso,
    @OneToOne(mappedBy = "kayttajaTunniste") val hankeKayttaja: HankeKayttajaEntity?
) {

    fun toDomain() =
        KayttajaTunniste(id, tunniste, createdAt, sentAt, kayttooikeustaso, hankeKayttaja?.id)

    companion object {
        private const val tokenLength: Int = 24
        private val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        private val secureRandom: SecureRandom = SecureRandom()

        fun create(sentAt: OffsetDateTime? = null) =
            KayttajaTunnisteEntity(
                tunniste = randomToken(),
                createdAt = getCurrentTimeUTC().toOffsetDateTime(),
                sentAt = sentAt,
                kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
                hankeKayttaja = null
            )

        private fun randomToken(): String =
            secureRandom
                .ints(tokenLength.toLong(), 0, charPool.size)
                .asSequence()
                .map(charPool::get)
                .joinToString("")
    }
}

@Repository
interface KayttajaTunnisteRepository : JpaRepository<KayttajaTunnisteEntity, UUID> {
    fun findByTunniste(tunniste: String): KayttajaTunnisteEntity?
}
