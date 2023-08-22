package fi.hel.haitaton.hanke.logging

import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.permissions.KayttajaTunniste
import fi.hel.haitaton.hanke.permissions.PermissionEntity
import fi.hel.haitaton.hanke.permissions.Role
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class HankeKayttajaLoggingService(private val auditLogService: AuditLogService) {

    @Transactional(propagation = Propagation.MANDATORY)
    fun logCreate(savedApplication: Application, userId: String) {
        auditLogService.create(
            AuditLogService.createEntry(userId, ObjectType.APPLICATION, savedApplication)
        )
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun logUpdate(roleBefore: Role, permissionEntityAfter: PermissionEntity, userId: String) {
        val permissionAfter = permissionEntityAfter.toDomain()
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

    @Transactional(propagation = Propagation.MANDATORY)
    fun logDelete(applicationBefore: Application, userId: String) {
        auditLogService.create(
            AuditLogService.deleteEntry(userId, ObjectType.APPLICATION, applicationBefore)
        )
    }
}
