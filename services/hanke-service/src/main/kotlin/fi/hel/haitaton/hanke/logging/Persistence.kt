package fi.hel.haitaton.hanke.logging

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDateTime
import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table

/**
 * Type of action/event.
 * isChange-value tells whether the action can or could have done changes
 * to the business data it is about. Those that can not change such data
 * will not get an entry in the changelog (but can still get one in the auditlog).
 */
enum class Action(val isChange: Boolean) {
    /**
     * When some new "business data" object is created.
     */
    CREATE(true),
    /**
     * When some (sensitive) "business data" is read.
     */
    READ(false),
    /**
     * When some "business data" is changed.
     */
    UPDATE(true),
    /**
     * When some "business data" object is deleted. (Note, the whole object,
     * not just one or more fields in it.)
     */
    DELETE(true),
    /**
     * To record a change of the datalocked-field to "true". Not done by
     * Haitaton itself (for now), so add a row with this action when manually
     * setting that restriction flag.
     */
    LOCK(false),
    /**
     * To record a change of the datalocked-field from "true" to "false".
     * Not done by Haitaton itself (for now), so add a row with this action
     * when manually setting that restriction flag.
     */
    UNLOCK(false)
}

@Entity
@Table(schema = "personaldatalogs", name = "auditlog" )
class AuditLogEntry (
    var eventTime: LocalDateTime? = null,
    var userId: String? = null,
    var actor: String? = null,
    var ipNear: String? = null,
    var ipFar: String? = null,
    // Note, this can be briefly null during creation of a new YhteystietoEntity
    var yhteystietoId: Int? = 0,
    @Enumerated(EnumType.STRING)
    var action: Action? = null,
    // null is to be considered equal to false
    var failed: Boolean? = null,
    var description: String? = null
) {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null
}

@Entity
@Table(schema = "personaldatalogs", name = "changelog" )
class ChangeLogEntry (
    var eventTime: LocalDateTime? = null,
    // Note, this can be briefly null during creation of a new YhteystietoEntity
    var yhteystietoId: Int? = 0,
    @Enumerated(EnumType.STRING)
    var action: Action? = null,
    // null is to be considered equal to false
    var failed: Boolean? = null,
    var oldData: String? = null,
    var newData: String? = null
) {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null
}

interface PersonalDataAuditLogRepository : JpaRepository<AuditLogEntry, Int> {
    // No need for additional functions. Only adding entries from Haitaton app.
}

interface PersonalDataChangeLogRepository : JpaRepository<ChangeLogEntry, Int> {
    // No need for additional functions. Only adding entries from Haitaton app.
}
