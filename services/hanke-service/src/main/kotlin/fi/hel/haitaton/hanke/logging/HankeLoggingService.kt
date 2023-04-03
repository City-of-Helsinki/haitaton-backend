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
        val auditLogEntry = AuditLogService.deleteEntry(userId, ObjectType.HANKE, hanke)
        val yhteystietoEntries =
            (hanke.rakennuttajat + hanke.toteuttajat + hanke.omistajat).map {
                AuditLogService.deleteEntry(userId, ObjectType.YHTEYSTIETO, it)
            }

        auditLogService.createAll(yhteystietoEntries + auditLogEntry)
    }

    /**
     * Create audit log entry for a created hanke.
     *
     * Don't process sub-entities, they are handled elsewhere. Log entries for yhteystiedot are
     * added in [fi.hel.haitaton.hanke.HankeServiceImpl]. Geometries are added in their own
     * controller, so they are logged there.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun logCreate(hanke: Hanke, userId: String) {
        auditLogService.create(AuditLogService.createEntry(userId, ObjectType.HANKE, hanke))
    }

    /**
     * Create audit log entry for an updated hanke.
     *
     * Only create the entry if the logged content of the hanke has changed.
     *
     * Currently, the version field is updated even if there are no other changes, so the audit log
     * entry is always created.
     *
     * Don't process sub-entities, they are handled elsewhere. Log entries for yhteystiedot are
     * added in [fi.hel.haitaton.hanke.HankeServiceImpl]. Geometries are added in their own
     * controller, so they are logged there.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun logUpdate(hankeBefore: Hanke, hankeAfter: Hanke, userId: String) {
        AuditLogService.updateEntry(userId, ObjectType.HANKE, hankeBefore, hankeAfter)?.let {
            auditLogService.create(it)
        }
    }
}
