package fi.hel.haitaton.hanke.allu

import java.time.ZonedDateTime

data class ApplicationHistorySearch(val applicationIds: List<Int>, val eventsAfter: ZonedDateTime?)

data class ApplicationHistory(
    val applicationId: Int,
    val events: List<ApplicationStatusEvent>,
    val supervisionEvents: List<SupervisionEvent>,
)

data class ApplicationStatusEvent(
    val eventTime: ZonedDateTime,
    val newStatus: ApplicationStatus,
    val applicationIdentifier: String,
    /**
     * Tells next status (DECISION, OPERATIONAL_CONDITION or FINISHED) if current status is
     * DECISIONMAKING
     */
    val targetStatus: ApplicationStatus?,
)

enum class ApplicationStatus {
    PENDING_CLIENT, // Application pending on client? Missing from API docs
    PENDING, // Application received
    WAITING_INFORMATION, // Application waiting response to information request
    INFORMATION_RECEIVED, // Response to information request received
    HANDLING, // Application handling started
    RETURNED_TO_PREPARATION, // Returned to preparation by decision maker
    WAITING_CONTRACT_APPROVAL, // Waiting approval of contract
    APPROVED, // Contract approved
    REJECTED, // Contract rejected
    DECISIONMAKING, // Waiting decision
    DECISION, // Decision made
    OPERATIONAL_CONDITION, // Application in operational condition
    TERMINATED, // Application terminated
    FINISHED, // Application finished
    CANCELLED, // // Application cancelled
    ARCHIVED, // Application archived
}

data class SupervisionEvent(
    val eventTime: ZonedDateTime,
    val type: SupervisionTaskType,
    val status: SupervisionTaskStatusType,
    val comment: String?, // This might be non-nullable
)

enum class SupervisionTaskType {
    PRELIMINARY_SUPERVISION,
    OPERATIONAL_CONDITION,
    SUPERVISION,
    WORK_TIME_SUPERVISION,
    FINAL_SUPERVISION,
    WARRANTY,
    TERMINATION,
}

enum class SupervisionTaskStatusType {
    APPROVED,
    REJECTED,
    OPEN,
    CANCELLED,
}
