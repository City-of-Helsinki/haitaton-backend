package fi.hel.haitaton.hanke.logging

import org.springframework.stereotype.Service

/** Service for methods common to all types of audit logs. */
@Service
class AuditLogService(private val auditLogRepository: AuditLogRepository) {

    /** Save audit log entries. Converts them to entities and saves them. */
    fun saveAll(auditLogEntries: Collection<AuditLogEntry>): MutableList<AuditLogEntryEntity> {
        return auditLogRepository.saveAll(auditLogEntries.map { it.toEntity() })
    }
}
