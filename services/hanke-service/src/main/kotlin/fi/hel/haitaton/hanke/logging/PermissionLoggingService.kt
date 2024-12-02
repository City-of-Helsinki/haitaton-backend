package fi.hel.haitaton.hanke.logging

import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.permissions.Permission
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
class PermissionLoggingService(private val auditLogService: AuditLogService) :
    ChangeLoggingService<Int, Permission>(auditLogService) {
    override val objectType: ObjectType = ObjectType.PERMISSION

    @Transactional(propagation = Propagation.MANDATORY)
    fun logUpdate(
        kayttooikeustasoBefore: Kayttooikeustaso,
        permissionAfter: Permission,
        userId: String
    ) {
        val permissionBefore = permissionAfter.copy(kayttooikeustaso = kayttooikeustasoBefore)

        AuditLogService.updateEntry(
                userId, ObjectType.PERMISSION, permissionBefore, permissionAfter)
            ?.let { auditLogService.create(it) }
    }
}
