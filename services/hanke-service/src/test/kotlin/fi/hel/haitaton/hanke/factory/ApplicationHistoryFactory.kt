package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.allu.ApplicationHistory
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.ApplicationStatusEvent
import java.time.ZonedDateTime

object ApplicationHistoryFactory {

    val defaultApplicationId = 1
    val defaultApplicationIdentifier = "JS2300001"
    val defaultEventTime = ZonedDateTime.parse("2022-10-12T15:25:34.981654Z")
    val defaultStatus = ApplicationStatus.PENDING
    val defaultTargetStatus: ApplicationStatus? = null

    /**
     * Create a history for an application with two events at different times. Supervision events
     * are not included.
     */
    fun create(
        applicationId: Int = defaultApplicationId,
        applicationIdentifier: String = defaultApplicationIdentifier,
    ): ApplicationHistory =
        ApplicationHistory(
            applicationId,
            events =
                listOf(
                    createEvent(
                        eventTime = ZonedDateTime.parse("2022-10-12T15:25:34.981654Z[UTC]"),
                        newStatus = ApplicationStatus.PENDING,
                        applicationIdentifier = applicationIdentifier,
                    ),
                    createEvent(
                        eventTime = ZonedDateTime.parse("2023-01-09T14:37:09.135Z[UTC]"),
                        newStatus = ApplicationStatus.PENDING_CLIENT,
                        applicationIdentifier = applicationIdentifier,
                    ),
                ),
            supervisionEvents = listOf()
        )

    fun create(
        applicationId: Int = defaultApplicationId,
        vararg events: ApplicationStatusEvent,
    ): ApplicationHistory =
        ApplicationHistory(applicationId, events = events.toList(), supervisionEvents = listOf())

    /** Create a status event for an application. */
    fun createEvent(
        eventTime: ZonedDateTime = defaultEventTime,
        newStatus: ApplicationStatus = defaultStatus,
        applicationIdentifier: String = defaultApplicationIdentifier,
        targetStatus: ApplicationStatus? = defaultTargetStatus,
    ) =
        ApplicationStatusEvent(
            eventTime = eventTime,
            newStatus = newStatus,
            applicationIdentifier = applicationIdentifier,
            targetStatus = targetStatus
        )
}
