package fi.hel.haitaton.hanke.logging

import fi.hel.haitaton.hanke.muutosilmoitus.Muutosilmoitus
import java.util.UUID
import org.springframework.stereotype.Service

@Service
class MuutosilmoitusLoggingService(auditLogService: AuditLogService) :
    ChangeLoggingService<UUID, Muutosilmoitus>(auditLogService) {
    override val objectType: ObjectType = ObjectType.MUUTOSILMOITUS
}
