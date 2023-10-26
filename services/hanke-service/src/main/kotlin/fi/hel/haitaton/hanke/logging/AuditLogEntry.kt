package fi.hel.haitaton.hanke.logging

import java.time.OffsetDateTime

/**
 * Type of operation/event.
 *
 * @param isChange tells whether the operation can change the business data
 */
enum class Operation(val isChange: Boolean) {
    /** When some new "business data" object is created. */
    CREATE(true),

    /** When some (sensitive) "business data" is read. */
    READ(false),

    /** When some "business data" is changed. */
    UPDATE(true),

    /**
     * When some "business data" object is deleted. (Note, the whole object, not just one or more
     * fields in it.)
     */
    DELETE(true),

    /**
     * To record a change of the data locked -field to "true". Not done by Haitaton itself (for
     * now), so add a row with this operation when manually setting that restriction flag.
     */
    LOCK(false),

    /**
     * To record a change of the data locked -field from "true" to "false". Not done by Haitaton
     * itself (for now), so add a row with this operation when manually setting that restriction
     * flag.
     */
    UNLOCK(false)
}

enum class Status {
    SUCCESS,
    FAILED
}

enum class ObjectType {
    APPLICATION,
    ALLU_CONTACT,
    ALLU_CUSTOMER,
    APPLICATION_CONTACT,
    APPLICATION_CUSTOMER,
    CABLE_REPORT,
    GDPR_RESPONSE,
    HANKE,
    HANKE_KAYTTAJA,
    KAYTTAJA_TUNNISTE,
    PERMISSION,
    YHTEYSTIETO,
}

enum class UserRole {
    USER,
    SERVICE,
}

/** Flat [AuditLogMessage] for better ergonomics. */
data class AuditLogEntry(
    val operation: Operation,
    val status: Status,
    val failureDescription: String? = null,
    val userId: String? = null,
    val userRole: UserRole = UserRole.USER,
    val objectId: String,
    val objectType: ObjectType,
    val objectBefore: String? = null,
    val objectAfter: String? = null,
) {
    fun toEntity(dateTime: OffsetDateTime, ipAddress: String?): AuditLogEntryEntity =
        AuditLogEntryEntity(
            message =
                AuditLogMessage(
                    AuditLogEvent(
                        dateTime = dateTime,
                        operation = operation,
                        status = status,
                        failureDescription = failureDescription,
                        actor = AuditLogActor(userId, userRole, ipAddress),
                        target = AuditLogTarget(objectId, objectType, objectBefore, objectAfter),
                    )
                )
        )
}
