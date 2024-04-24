package fi.hel.haitaton.hanke.logging

import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.permissions.Permission
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class PermissionLoggingService(private val auditLogService: AuditLogService) {
    @Transactional(propagation = Propagation.MANDATORY)
    fun logUpdate(
        kayttooikeustasoBefore: Kayttooikeustaso,
        permissionAfter: Permission,
        userId: String
    ) {
        val permissionBefore = permissionAfter.copy(kayttooikeustaso = kayttooikeustasoBefore)

        AuditLogService.updateEntry(
                userId,
                ObjectType.PERMISSION,
                permissionBefore,
                permissionAfter,
            )
            ?.let { auditLogService.create(it) }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun logCreate(permission: Permission, userId: String) {
        auditLogService.create(
            AuditLogService.createEntry(userId, ObjectType.PERMISSION, permission)
        )
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun logDelete(permission: Permission, currentUserId: String) {
        auditLogService.create(
            AuditLogService.deleteEntry(currentUserId, ObjectType.PERMISSION, permission)
        )
    }
}
