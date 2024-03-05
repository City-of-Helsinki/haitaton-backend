package fi.hel.haitaton.hanke.logging

import fi.hel.haitaton.hanke.hakemus.Hakemus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class HakemusLoggingService(private val auditLogService: AuditLogService) {

    @Transactional(propagation = Propagation.MANDATORY)
    fun logCreate(savedHakemus: Hakemus, userId: String) {
        auditLogService.create(
            AuditLogService.createEntry(userId, ObjectType.HAKEMUS, savedHakemus)
        )
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun logUpdate(hakemusBefore: Hakemus, hakemusAfter: Hakemus, userId: String) {
        AuditLogService.updateEntry(userId, ObjectType.HAKEMUS, hakemusBefore, hakemusAfter)?.let {
            auditLogService.create(it)
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun logDelete(hakemusBefore: Hakemus, userId: String) {
        auditLogService.create(
            AuditLogService.deleteEntry(userId, ObjectType.HAKEMUS, hakemusBefore)
        )
    }
}
