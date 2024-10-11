package fi.hel.haitaton.hanke.logging

import fi.hel.haitaton.hanke.hakemus.Hakemus
import org.springframework.stereotype.Service

@Service
class HakemusLoggingService(auditLogService: AuditLogService) :
    ChangeLoggingService<Long, Hakemus>(auditLogService) {
    override val objectType: ObjectType = ObjectType.HAKEMUS
}
