package fi.hel.haitaton.hanke.logging

import fi.hel.haitaton.hanke.domain.Hanke
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class HankeLoggingService(private val auditLogService: AuditLogService) {

    /**
     * Create audit log entry for a deleted hanke.
     *
     * This also creates entries for sub-entities (yhteystiedot and geometries), since they will be
     * deleted with the hanke, and they are not handled anywhere else.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun logDelete(hanke: Hanke, userId: String) {
        // TODO: Add geometries in auditLogEntry or as separate log entries.
        //   Waits for the changes in HAI-1273 before implementing.
        val auditLogEntry = AuditLogService.deleteEntry(userId, ObjectType.HANKE, hanke)
        val yhteystietoEntries =
            (hanke.arvioijat + hanke.toteuttajat + hanke.omistajat).map {
                AuditLogService.deleteEntry(userId, ObjectType.YHTEYSTIETO, it)
            }

        auditLogService.createAll(yhteystietoEntries + auditLogEntry)
    }
}
