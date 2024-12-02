package fi.hel.haitaton.hanke.logging

import fi.hel.haitaton.hanke.domain.HasId
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

abstract class ChangeLoggingService<ID, T : HasId<ID>>(
    private val auditLogService: AuditLogService
) {
    abstract val objectType: ObjectType

    @Transactional(propagation = Propagation.MANDATORY)
    open fun logCreate(saved: T, userId: String) {
        auditLogService.create(AuditLogService.createEntry(userId, objectType, saved))
    }

    @Transactional(propagation = Propagation.MANDATORY)
    open fun logUpdate(before: T, after: T, userId: String) {
        AuditLogService.updateEntry(userId, objectType, before, after)?.let {
            auditLogService.create(it)
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    open fun logDelete(before: T, userId: String) {
        auditLogService.create(AuditLogService.deleteEntry(userId, objectType, before))
    }
}
