package fi.hel.haitaton.hanke.test

import assertk.Assert
import assertk.all
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import fi.hel.haitaton.hanke.logging.AuditLogActor
import fi.hel.haitaton.hanke.logging.AuditLogEntryEntity
import fi.hel.haitaton.hanke.logging.AuditLogEvent
import fi.hel.haitaton.hanke.logging.AuditLogMessage
import fi.hel.haitaton.hanke.logging.AuditLogTarget
import fi.hel.haitaton.hanke.logging.ObjectType
import fi.hel.haitaton.hanke.logging.Operation
import fi.hel.haitaton.hanke.logging.Status
import fi.hel.haitaton.hanke.logging.UserRole
import fi.hel.haitaton.hanke.parseJson
import fi.hel.haitaton.hanke.test.Asserts.isRecent

object AuditLogEntryEntityAsserts {
    fun Assert<AuditLogEntryEntity>.auditEvent() =
        prop(AuditLogEntryEntity::message).prop(AuditLogMessage::auditEvent)

    fun Assert<AuditLogEntryEntity>.auditEvent(body: Assert<AuditLogEvent>.() -> Unit) =
        auditEvent().all { body(this) }

    private fun Assert<AuditLogActor>.isUser(userId: String) =
        this.all {
            prop(AuditLogActor::role).isEqualTo(UserRole.USER)
            prop(AuditLogActor::userId).isNotNull().isEqualTo(userId)
        }

    fun Assert<AuditLogEvent>.hasUserActor(userId: String) =
        prop(AuditLogEvent::actor).isUser(userId)

    fun Assert<AuditLogEvent>.hasUserActor(userId: String, ipAddress: String) =
        prop(AuditLogEvent::actor).all {
            isUser(userId)
            prop(AuditLogActor::ipAddress).isEqualTo(ipAddress)
        }

    fun Assert<AuditLogEvent>.hasServiceActor(userId: String) =
        prop(AuditLogEvent::actor).all {
            prop(AuditLogActor::role).isEqualTo(UserRole.SERVICE)
            prop(AuditLogActor::userId).isEqualTo(userId)
        }

    fun Assert<AuditLogEvent>.hasMockedIp(ipAddress: String = TestUtils.mockedIp) =
        prop(AuditLogEvent::actor).prop(AuditLogActor::ipAddress).isEqualTo(ipAddress)

    fun Assert<AuditLogEvent>.withTarget(body: Assert<AuditLogTarget>.() -> Unit) =
        prop(AuditLogEvent::target).all(body)

    inline fun <reified T> Assert<AuditLogTarget>.hasObjectBefore(before: T) =
        hasObjectBefore<T> { isEqualTo(before) }

    inline fun <reified T> Assert<AuditLogTarget>.hasObjectBefore(
        crossinline body: Assert<T>.() -> Unit
    ) =
        prop(AuditLogTarget::objectBefore)
            .isNotNull()
            .transform { it.parseJson<T>() }
            .all { body(this) }

    inline fun <reified T> Assert<AuditLogTarget>.hasObjectAfter(after: T) =
        hasObjectAfter<T> { isEqualTo(after) }

    inline fun <reified T> Assert<AuditLogTarget>.hasObjectAfter(
        crossinline body: Assert<T>.() -> Unit
    ) =
        prop(AuditLogTarget::objectAfter)
            .isNotNull()
            .transform { it.parseJson<T>() }
            .all { body(this) }

    fun Assert<AuditLogTarget>.hasId(id: Any) =
        prop(AuditLogTarget::id).isNotNull().isEqualTo(id.toString())

    fun Assert<AuditLogTarget>.hasTargetType(type: ObjectType) =
        prop(AuditLogTarget::type).isEqualTo(type)

    fun Assert<AuditLogEntryEntity>.isSuccess(
        operation: Operation,
        body: Assert<AuditLogEvent>.() -> Unit,
    ) = all {
        prop(AuditLogEntryEntity::isSent).isFalse()
        prop(AuditLogEntryEntity::createdAt).isRecent()
        prop(AuditLogEntryEntity::id).isNotNull()
        auditEvent {
            prop(AuditLogEvent::appVersion).isEqualTo("1")
            prop(AuditLogEvent::status).isEqualTo(Status.SUCCESS)
            prop(AuditLogEvent::operation).isEqualTo(operation)
            prop(AuditLogEvent::dateTime).isRecent()
            prop(AuditLogEvent::failureDescription).isNull()
            all(body)
        }
    }
}
