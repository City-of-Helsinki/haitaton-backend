package fi.hel.haitaton.hanke.logging

import fi.hel.haitaton.hanke.permissions.HankeKayttaja
import fi.hel.haitaton.hanke.permissions.KayttajaTunniste
import java.util.UUID
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class HankeKayttajaLoggingService(private val auditLogService: AuditLogService) :
    ChangeLoggingService<UUID, HankeKayttaja>(auditLogService) {

    override val objectType: ObjectType = ObjectType.HANKE_KAYTTAJA

    @Transactional(propagation = Propagation.MANDATORY)
    fun logUpdate(
        kayttajaTunnisteBefore: KayttajaTunniste,
        kayttajaTunnisteAfter: KayttajaTunniste,
        userId: String
    ) {
        AuditLogService.updateEntry(
                userId,
                ObjectType.KAYTTAJA_TUNNISTE,
                kayttajaTunnisteBefore,
                kayttajaTunnisteAfter,
            )
            ?.let { auditLogService.create(it) }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun logCreate(kayttajaTunniste: KayttajaTunniste, userId: String) {
        auditLogService.create(
            AuditLogService.createEntry(userId, ObjectType.KAYTTAJA_TUNNISTE, kayttajaTunniste))
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun logDelete(tunniste: KayttajaTunniste, currentUserId: String) {
        auditLogService.create(
            AuditLogService.deleteEntry(currentUserId, ObjectType.KAYTTAJA_TUNNISTE, tunniste))
    }
}
