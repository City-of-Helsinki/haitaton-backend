package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.allu.AlluEventEntity
import fi.hel.haitaton.hanke.allu.AlluEventRepository
import fi.hel.haitaton.hanke.allu.AlluEventStatus
import fi.hel.haitaton.hanke.allu.ApplicationStatusEvent
import org.springframework.stereotype.Component

@Component
class AlluEventFactory(private val alluEventRepository: AlluEventRepository) {

    companion object {
        fun createEntity(
            alluId: Int,
            event: ApplicationStatusEvent,
            status: AlluEventStatus = AlluEventStatus.PENDING,
        ) =
            AlluEventEntity(
                0,
                alluId = alluId,
                eventTime = event.eventTime.toOffsetDateTime(),
                newStatus = event.newStatus,
                applicationIdentifier = event.applicationIdentifier,
                targetStatus = event.targetStatus,
                status = status,
                stackTrace =
                    if (status == AlluEventStatus.FAILED) {
                        "Failed to process event for alluId $alluId: ${event.toLogString()}"
                    } else null,
            )
    }

    fun saveEventEntity(
        alluId: Int,
        event: ApplicationStatusEvent,
        status: AlluEventStatus = AlluEventStatus.PENDING,
    ): AlluEventEntity {
        val entity = createEntity(alluId, event, status)
        return alluEventRepository.save(entity)
    }
}
