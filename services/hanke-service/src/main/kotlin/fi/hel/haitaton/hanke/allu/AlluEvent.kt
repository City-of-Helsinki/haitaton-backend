package fi.hel.haitaton.hanke.allu

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.OffsetDateTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository

enum class AlluEventStatus {
    PENDING,
    PROCESSED,
    FAILED,
}

@Entity
@Table(name = "allu_event")
class AlluEventEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    val alluId: Int,
    val eventTime: OffsetDateTime,
    @Enumerated(EnumType.STRING) val newStatus: ApplicationStatus,
    val applicationIdentifier: String,
    @Enumerated(EnumType.STRING) val targetStatus: ApplicationStatus?,
    @Enumerated(EnumType.STRING) var status: AlluEventStatus = AlluEventStatus.PENDING,
    var stackTrace: String? = null,
    val createdAt: Instant = Instant.now(),
    var processedAt: Instant? = null,
    var retryCount: Int = 0,
) {
    fun toApplicationStatusEvent() =
        ApplicationStatusEvent(
            eventTime = eventTime.toZonedDateTime(),
            newStatus = newStatus,
            applicationIdentifier = applicationIdentifier,
            targetStatus = targetStatus,
        )

    fun toLogString(): String =
        "alluId=$alluId, eventTime=$eventTime applicationIdentifier=$applicationIdentifier, newStatus=$newStatus, status=$status"

    override fun toString(): String {
        return "AlluEventEntity(id=$id, alluId=$alluId, eventTime=$eventTime, newStatus=$newStatus, applicationIdentifier='$applicationIdentifier', targetStatus=$targetStatus, status=$status, stackTrace=$stackTrace, createdAt=$createdAt, processedAt=$processedAt, retryCount=$retryCount)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AlluEventEntity

        if (alluId != other.alluId) return false
        if (eventTime != other.eventTime) return false
        if (newStatus != other.newStatus) return false

        return true
    }

    override fun hashCode(): Int {
        var result = alluId
        result = 31 * result + eventTime.hashCode()
        result = 31 * result + newStatus.hashCode()
        return result
    }
}

interface AlluEventRepositoryCustom {
    fun batchInsertIgnoreDuplicates(events: List<AlluEventEntity>)

    fun deleteProcessedEventsOlderThan(time: OffsetDateTime)
}

@Repository
interface AlluEventRepository : JpaRepository<AlluEventEntity, Long>, AlluEventRepositoryCustom {
    fun findByStatusInOrderByAlluIdAscEventTimeAsc(
        statuses: List<AlluEventStatus>
    ): List<AlluEventEntity>
}

fun AlluEventRepository.findPendingAndFailedEventsGrouped(): Map<Int, List<AlluEventEntity>> =
    findByStatusInOrderByAlluIdAscEventTimeAsc(
            listOf(AlluEventStatus.PENDING, AlluEventStatus.FAILED)
        )
        .groupBy { it.alluId }

@Component
class AlluEventRepositoryImpl(private val jdbcTemplate: JdbcTemplate) : AlluEventRepositoryCustom {

    companion object {
        private const val BATCH_INSERT_SQL =
            """
            INSERT INTO allu_event (alluId, eventTime, newStatus, applicationIdentifier, targetStatus)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (alluId, eventTime, newStatus) DO NOTHING
        """
    }

    override fun batchInsertIgnoreDuplicates(events: List<AlluEventEntity>) {
        jdbcTemplate.batchUpdate(
            BATCH_INSERT_SQL,
            events.map { event ->
                arrayOf<Any?>(
                    event.alluId,
                    event.eventTime,
                    event.newStatus.name,
                    event.applicationIdentifier,
                    event.targetStatus?.name,
                )
            },
        )
    }

    override fun deleteProcessedEventsOlderThan(time: OffsetDateTime) {
        jdbcTemplate.update(
            "DELETE FROM allu_event WHERE status = 'PROCESSED' AND eventtime < ?",
            time,
        )
    }
}
