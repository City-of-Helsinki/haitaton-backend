package fi.hel.haitaton.hanke.logging

import fi.hel.haitaton.hanke.domain.Hanke
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class HankeLoggingService(private val auditLogService: AuditLogService) :
    ChangeLoggingService<Int, Hanke>(auditLogService) {

    override val objectType: ObjectType = ObjectType.HANKE

    /**
     * Create audit log entry for a deleted hanke.
     *
     * This also creates entries for sub-entities (yhteystiedot and geometries), since they will be
     * deleted with the hanke, and they are not handled anywhere else.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    override fun logDelete(before: Hanke, userId: String) {
        val auditLogEntry = AuditLogService.deleteEntry(userId, ObjectType.HANKE, before)
        val yhteystietoEntries =
            before.extractYhteystiedot().map {
                AuditLogService.deleteEntry(userId, ObjectType.YHTEYSTIETO, it)
            }

        auditLogService.createAll(yhteystietoEntries + auditLogEntry)
    }

    /**
     * Create audit log entry for a hanke deleted by Haitaton.
     *
     * This also creates entries for sub-entities (yhteystiedot), since they will be deleted with
     * the hanke, and they are not handled anywhere else.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    override fun logDeleteFromHaitaton(before: Hanke) {
        val auditLogEntry = AuditLogService.deleteEntryForHaitaton(ObjectType.HANKE, before)
        val yhteystietoEntries =
            before.extractYhteystiedot().map {
                AuditLogService.deleteEntryForHaitaton(ObjectType.YHTEYSTIETO, it)
            }

        auditLogService.createAll(yhteystietoEntries + auditLogEntry)
    }

    fun createAll(auditLogEntries: List<AuditLogEntry>) = auditLogService.createAll(auditLogEntries)
}
