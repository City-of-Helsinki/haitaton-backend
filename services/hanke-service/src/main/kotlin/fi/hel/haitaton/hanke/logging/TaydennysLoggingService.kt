package fi.hel.haitaton.hanke.logging

import fi.hel.haitaton.hanke.taydennys.Taydennys
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class TaydennysLoggingService(auditLogService: AuditLogService) :
    ChangeLoggingService<UUID, Taydennys>(auditLogService) {
    override val objectType: ObjectType = ObjectType.TAYDENNYS
}
