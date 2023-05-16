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
import mu.KotlinLogging
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

private val logger = KotlinLogging.logger {}

@Entity
@Table(name = "kayttaja_tunniste")
class KayttajaTunnisteEntity(
    @Id val id: UUID = UUID.randomUUID(),
    val tunniste: String,
    @Column(name = "created_at") val createdAt: OffsetDateTime,
    @Column(name = "sent_at") val sentAt: OffsetDateTime?,
    @Enumerated(EnumType.STRING) val role: Role,
    @OneToOne(mappedBy = "kayttajaTunniste") val hankeKayttaja: HankeKayttajaEntity?
) {

    companion object {
        private const val tokenLength: Int = 24
        private val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        private val secureRandom: SecureRandom = SecureRandom()

        fun create() =
            KayttajaTunnisteEntity(
                tunniste = randomToken(),
                createdAt = getCurrentTimeUTC().toOffsetDateTime(),
                sentAt = null,
                role = Role.KATSELUOIKEUS,
                hankeKayttaja = null
            )

        private fun randomToken(): String {
            logger.info { "Generating random token" }
            val token =
                secureRandom
                    .ints(tokenLength.toLong(), 0, charPool.size)
                    .asSequence()
                    .map(charPool::get)
                    .joinToString("")
            logger.info { "Random token generated" }
            logger.info {
                "java.security.egd = ${System.getProperty("java.security.egd")} and used the ${secureRandom.algorithm} algorithm"
            }
            return token
        }
    }
}

@Repository interface KayttajaTunnisteRepository : JpaRepository<KayttajaTunnisteEntity, UUID> {}
