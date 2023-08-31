package fi.hel.haitaton.hanke.logging

import fi.hel.haitaton.hanke.permissions.KayttajaTunniste
import fi.hel.haitaton.hanke.permissions.Permission
import fi.hel.haitaton.hanke.permissions.Role
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class HankeKayttajaLoggingService(private val auditLogService: AuditLogService) {

    @Transactional(propagation = Propagation.MANDATORY)
    fun logUpdate(roleBefore: Role, permissionAfter: Permission, userId: String) {
        val permissionBefore = permissionAfter.copy(role = roleBefore)

        AuditLogService.updateEntry(
                userId,
                ObjectType.PERMISSION,
                permissionBefore,
                permissionAfter,
            )
            ?.let { auditLogService.create(it) }
    }

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
}
