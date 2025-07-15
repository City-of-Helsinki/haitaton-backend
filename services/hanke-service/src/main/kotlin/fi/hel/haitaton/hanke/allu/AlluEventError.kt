package fi.hel.haitaton.hanke.allu

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.ZonedDateTime
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

data class AlluEventError(
    val id: Long,
    val alluId: Int,
    val eventTime: ZonedDateTime,
    val newStatus: ApplicationStatus,
    val applicationIdentifier: String,
    val targetStatus: ApplicationStatus? = null,
    val stackTrace: String? = null,
) {
    constructor(
        history: ApplicationHistory,
        event: ApplicationStatusEvent,
        stackTrace: String?,
    ) : this(
        id = 0,
        alluId = history.applicationId,
        eventTime = event.eventTime,
        newStatus = event.newStatus,
        applicationIdentifier = event.applicationIdentifier,
        targetStatus = event.targetStatus,
        stackTrace = stackTrace,
    )

    fun toLogString(): String =
        "alluId=$alluId, applicationIdentifier=$applicationIdentifier, newStatus=$newStatus"

    fun toApplicationStatusEvent() =
        ApplicationStatusEvent(
            eventTime = eventTime,
            newStatus = newStatus,
            applicationIdentifier = applicationIdentifier,
            targetStatus = targetStatus,
        )

    fun toApplicationHistory(): ApplicationHistory =
        ApplicationHistory(
            applicationId = alluId,
            events = listOf(toApplicationStatusEvent()),
            supervisionEvents = emptyList(),
        )
}

@Entity
@Table(name = "allu_event_error")
class AlluEventErrorEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    val alluId: Int,
    val eventTime: ZonedDateTime,
    @Enumerated(EnumType.STRING) val newStatus: ApplicationStatus,
    val applicationIdentifier: String,
    @Enumerated(EnumType.STRING) val targetStatus: ApplicationStatus?,
    val stackTrace: String?,
    val createdAt: Instant? = Instant.now(),
) {
    fun toDomain(): AlluEventError =
        AlluEventError(
            id = id,
            alluId = alluId,
            eventTime = eventTime,
            newStatus = newStatus,
            applicationIdentifier = applicationIdentifier,
            targetStatus = targetStatus,
            stackTrace = stackTrace,
        )
}

@Repository interface AlluEventErrorRepository : JpaRepository<AlluEventErrorEntity, Long>
