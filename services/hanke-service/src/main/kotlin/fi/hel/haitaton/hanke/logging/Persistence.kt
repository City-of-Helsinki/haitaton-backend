package fi.hel.haitaton.hanke.logging

import java.time.OffsetDateTime
import java.util.*
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.Id
import javax.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository

/**
 * Type of action/event.
 *
 * @param isChange tells whether the action can change the business data
 */
enum class Action(val isChange: Boolean) {
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
     * now), so add a row with this action when manually setting that restriction flag.
     */
    LOCK(false),

    /**
     * To record a change of the data locked -field from "true" to "false". Not done by Haitaton
     * itself (for now), so add a row with this action when manually setting that restriction flag.
     */
    UNLOCK(false)
}

enum class Status {
    SUCCESS,
    FAILED
}

enum class ObjectType {
    YHTEYSTIETO,
    ALLU_CUSTOMER,
    ALLU_CONTACT,
    GDPR_RESPONSE,
}

enum class UserRole {
    USER,
    SERVICE,
}

@Entity
@Table(name = "audit_log")
data class AuditLogEntry(
    @Id var id: UUID? = UUID.randomUUID(),
    @Column(name = "event_time") var eventTime: OffsetDateTime? = OffsetDateTime.now(),
    @Column(name = "user_id") var userId: String? = null,
    @Enumerated(EnumType.STRING) @Column(name = "user_role") var userRole: UserRole = UserRole.USER,
    @Column(name = "ip_near") var ipNear: String? = null,
    @Column(name = "ip_far") var ipFar: String? = null,
    @Enumerated(EnumType.STRING) var action: Action? = null,
    @Enumerated(EnumType.STRING) var status: Status? = null,
    @Column(name = "failure_description") var failureDescription: String? = null,
    @Enumerated(EnumType.STRING) @Column(name = "object_type") var objectType: ObjectType? = null,
    @Column(name = "object_id") var objectId: String? = null,
    @Column(name = "object_before") var objectBefore: String? = null,
    @Column(name = "object_after") var objectAfter: String? = null
)

interface AuditLogRepository : JpaRepository<AuditLogEntry, UUID> {
    // No need for additional functions. Only adding entries from Haitaton app.
}
