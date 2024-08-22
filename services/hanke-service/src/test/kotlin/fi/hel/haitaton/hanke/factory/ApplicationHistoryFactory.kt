package fi.hel.haitaton.hanke.factory

import fi.hel.haitaton.hanke.allu.ApplicationHistory
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.ApplicationStatusEvent
import java.time.ZonedDateTime

object ApplicationHistoryFactory {

    const val DEFAULT_APPLICATION_ID: Int = 1
    const val DEFAULT_APPLICATION_IDENTIFIER: String = "JS2300001"
    val DEFAULT_EVENT_TIME: ZonedDateTime = ZonedDateTime.parse("2022-10-12T15:25:34.981654Z")
    val DEFAULT_STATUS: ApplicationStatus = ApplicationStatus.PENDING
    val DEFAULT_TARGET_STATUS: ApplicationStatus? = null

    /**
     * Create a history for an application with two events at different times. Supervision events
     * are not included.
     */
    fun create(
        applicationId: Int = DEFAULT_APPLICATION_ID,
        applicationIdentifier: String = DEFAULT_APPLICATION_IDENTIFIER,
    ): ApplicationHistory =
        ApplicationHistory(
            applicationId,
            events =
                listOf(
                    createEvent(
                        eventTime = ZonedDateTime.parse("2022-10-12T15:25:34.981654Z"),
                        newStatus = ApplicationStatus.PENDING,
                        applicationIdentifier = applicationIdentifier,
                    ),
                    createEvent(
                        eventTime = ZonedDateTime.parse("2023-01-09T14:37:09.135Z"),
                        newStatus = ApplicationStatus.PENDING_CLIENT,
                        applicationIdentifier = applicationIdentifier,
                    ),
                ),
            supervisionEvents = listOf()
        )

    fun create(
        applicationId: Int = DEFAULT_APPLICATION_ID,
        vararg events: ApplicationStatusEvent,
    ): ApplicationHistory =
        ApplicationHistory(applicationId, events = events.toList(), supervisionEvents = listOf())

    /** Create a status event for an application. */
    fun createEvent(
        eventTime: ZonedDateTime = DEFAULT_EVENT_TIME,
        newStatus: ApplicationStatus = DEFAULT_STATUS,
        applicationIdentifier: String = DEFAULT_APPLICATION_IDENTIFIER,
        targetStatus: ApplicationStatus? = DEFAULT_TARGET_STATUS,
    ) =
        ApplicationStatusEvent(
            eventTime = eventTime,
            newStatus = newStatus,
            applicationIdentifier = applicationIdentifier,
            targetStatus = targetStatus
        )
}
