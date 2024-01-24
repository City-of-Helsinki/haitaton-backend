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
import jakarta.persistence.JoinColumn
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
    var kayttooikeustaso: Kayttooikeustaso,
    val hankeKayttajaId: UUID?
) : HasId<UUID>

@Entity
@Table(name = "kayttajakutsu")
class KayttajakutsuEntity(
    @Id val id: UUID = UUID.randomUUID(),
    val tunniste: String,
    @Column(name = "created_at") val createdAt: OffsetDateTime,
    @Enumerated(EnumType.STRING) var kayttooikeustaso: Kayttooikeustaso,
    @OneToOne
    @JoinColumn(name = "hankekayttaja_id", updatable = false, nullable = false, unique = true)
    val hankekayttaja: HankekayttajaEntity,
) {

    fun toDomain() = KayttajaTunniste(id, tunniste, createdAt, kayttooikeustaso, hankekayttaja.id)

    companion object {
        private const val tokenLength: Int = 24
        private val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        private val secureRandom: SecureRandom = SecureRandom()

        fun create(hankeKayttaja: HankekayttajaEntity) =
            KayttajakutsuEntity(
                tunniste = randomToken(),
                createdAt = getCurrentTimeUTC().toOffsetDateTime(),
                kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
                hankekayttaja = hankeKayttaja
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
interface KayttajakutsuRepository : JpaRepository<KayttajakutsuEntity, UUID> {
    fun findByTunniste(tunniste: String): KayttajakutsuEntity?
}
