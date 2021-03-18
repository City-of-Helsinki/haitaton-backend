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
 * Used in 'changelog'.
 */
enum class ChangeAction {
    CREATE,
    READ,
    UPDATE,
    DELETE
}

@Entity
@Table(schema = "personaldatalogs", name = "auditlog" )
class AuditLogEntry (
    var eventTime: LocalDateTime? = null,
    var userId: String? = null,
    var actor: String? = null,
    var ipNear: String? = null,
    var ipFar: String? = null,
    var yhteystietoId: Int = 0,
    var description: String? = null
) {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null
}

@Entity
@Table(schema = "personaldatalogs", name = "changelog" )
class ChangeLogEntry (
    var eventTime: LocalDateTime? = null,
    var yhteystietoId: Int = 0,
    @Enumerated(EnumType.STRING)
    var action: ChangeAction? = null,
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
