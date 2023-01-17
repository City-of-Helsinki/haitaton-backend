package fi.hel.haitaton.hanke.logging

import fi.hel.haitaton.hanke.application.Application
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class ApplicationLoggingService(private val auditLogService: AuditLogService) {

    @Transactional(propagation = Propagation.MANDATORY)
    fun logCreate(savedApplication: Application, userId: String) {
        auditLogService.create(
            AuditLogService.createEntry(userId, ObjectType.APPLICATION, savedApplication)
        )
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun logUpdate(applicationBefore: Application, applicationAfter: Application, userId: String) {
        AuditLogService.updateEntry(
                userId,
                ObjectType.APPLICATION,
                applicationBefore,
                applicationAfter
            )
            ?.let { auditLogService.create(it) }
    }
}
