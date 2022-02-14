package fi.hel.haitaton.hanke.application

import java.time.ZonedDateTime

data class ApplicationHistory(val applicationId: Int, val events: List<ApplicationStatusEvent>)

data class ApplicationStatusEvent(
        val eventTime: ZonedDateTime,
        val newStatus: ApplicationStatus,
        val applicationIdentifier: String,
        val targetStatus: ApplicationStatus // Tells next status (DECISION, OPERATIONAL_CONDITION or FINISHED) if current status is DECISIONMAKING
)

enum class ApplicationStatus {
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
    ARCHIVED; // Application archived
}
