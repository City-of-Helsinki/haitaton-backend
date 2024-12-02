package fi.hel.haitaton.hanke.logging

import fi.hel.haitaton.hanke.taydennys.Taydennyspyynto
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class TaydennyspyyntoLoggingService(auditLogService: AuditLogService) :
    ChangeLoggingService<UUID, Taydennyspyynto>(auditLogService) {
    override val objectType: ObjectType = ObjectType.TAYDENNYSPYYNTO
}
