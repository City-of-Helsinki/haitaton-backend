package fi.hel.haitaton.hanke.test

import assertk.Assert
import assertk.all
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import fi.hel.haitaton.hanke.logging.ALLU_AUDIT_LOG_USERID
import fi.hel.haitaton.hanke.logging.AuditLogEntry
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.logging.Operation
import fi.hel.haitaton.hanke.logging.Status
import fi.hel.haitaton.hanke.logging.UserRole
import fi.hel.haitaton.hanke.parseJson

object AuditLogEntryAsserts {
    fun Assert<AuditLogEntry>.isSuccess() = all {
        prop(AuditLogEntry::status).isEqualTo(Status.SUCCESS)
        prop(AuditLogEntry::failureDescription).isNull()
    }

    fun Assert<AuditLogEntry>.hasUserActor(userId: String) = all {
        prop(AuditLogEntry::userRole).isEqualTo(UserRole.USER)
        prop(AuditLogEntry::userId).isEqualTo(userId)
    }

    fun Assert<AuditLogEntry>.hasAlluActor() = all {
        prop(AuditLogEntry::userRole).isEqualTo(UserRole.SERVICE)
        prop(AuditLogEntry::userId).isEqualTo(ALLU_AUDIT_LOG_USERID)
    }

    inline fun <reified T> Assert<AuditLogEntry>.hasObjectAfter(after: T) = hasObjectAfter {
        isEqualTo(after)
    }

    inline fun <reified T> Assert<AuditLogEntry>.hasObjectAfter(
        crossinline body: Assert<T>.() -> Unit
    ) =
        prop(AuditLogEntry::objectAfter)
            .isNotNull()
            .transform { it.parseJson<T>() }
            .all { body(this) }

    inline fun <reified T> Assert<AuditLogEntry>.hasObjectBefore(before: T) = hasObjectBefore {
        isEqualTo(before)
    }

    inline fun <reified T> Assert<AuditLogEntry>.hasObjectBefore(
        crossinline body: Assert<T>.() -> Unit
    ) =
        prop(AuditLogEntry::objectBefore)
            .isNotNull()
            .transform { it.parseJson<T>() }
            .all { body(this) }

    fun Assert<AuditLogEntry>.hasObjectId(id: Any) =
        prop(AuditLogEntry::objectId).isNotNull().isEqualTo(id.toString())

    fun Assert<AuditLogEntry>.hasObjectType(type: ObjectType) =
        prop(AuditLogEntry::objectType).isNotNull().isEqualTo(type)

    fun Assert<AuditLogEntry>.isUpdate() =
        prop(AuditLogEntry::operation).isEqualTo(Operation.UPDATE)

    fun Assert<AuditLogEntry>.isCreate() =
        prop(AuditLogEntry::operation).isEqualTo(Operation.CREATE)
}
