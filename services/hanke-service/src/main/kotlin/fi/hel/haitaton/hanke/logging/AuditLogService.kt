package fi.hel.haitaton.hanke.logging

import fi.hel.haitaton.hanke.domain.HasId
import fi.hel.haitaton.hanke.toChangeLogJsonString
import java.time.OffsetDateTime
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/** Service for methods common to all types of audit logs. */
@Service
class AuditLogService(private val auditLogRepository: AuditLogRepository) {

    /**
     * Save new audit log entries. Converts them to entities and saves them.
     *
     * Sets the date_time and ip_address fields for every entry.
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    fun createAll(auditLogEntries: Collection<AuditLogEntry>): MutableList<AuditLogEntryEntity> {
        // Make sure all related logs have the same time
        val now = OffsetDateTime.now()
        return auditLogRepository.saveAll(auditLogEntries.map { it.toEntity(now, getIpAddress()) })
    }

    /**
     * Save a new audit log entry. Converts it to an entity and saves it.
     *
     * Sets the date_time and ip_address fields for the entry.
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    fun create(auditLogEntry: AuditLogEntry): MutableList<AuditLogEntryEntity> =
        createAll(listOf(auditLogEntry))

    companion object {
        /**
         * If request context (attributes) exists, returns the IP from it.
         *
         * TODO: very very very simplified implementation. Needs a lot of improvement.
         */
        fun getIpAddress(): String? {
            val attribs =
                (RequestContextHolder.getRequestAttributes() as ServletRequestAttributes?)
                    ?: return null
            val request = attribs.request
            // Very simplified version for now.
            // For starters, see e.g.
            // https://stackoverflow.com/questions/22877350/how-to-extract-ip-address-in-spring-mvc-controller-get-call
            // Combine all the various ideas into one, and note that even then it is not even
            // half-way to a proper solution. Hopefully one can find a ready-made fully thought out
            // implementation.
            var ip: String =
                request.getHeader("X-FORWARDED-FOR") ?: request.remoteAddr ?: return null
            // Just to make sure it won't break the db if someone put something silly long in the
            // header:
            if (ip.length > PERSONAL_DATA_LOGGING_MAX_IP_LENGTH)
                ip = ip.substring(0, PERSONAL_DATA_LOGGING_MAX_IP_LENGTH)

            return ip
        }

        fun <ID, T : HasId<ID>> deleteEntry(userId: String, type: ObjectType, deletedObject: T) =
            AuditLogEntry(
                operation = Operation.DELETE,
                status = Status.SUCCESS,
                userId = userId,
                userRole = UserRole.USER,
                objectId = deletedObject.id?.toString(),
                objectType = type,
                objectBefore = deletedObject.toChangeLogJsonString(),
                objectAfter = null,
            )

        fun <ID, T : HasId<ID>> createEntry(userId: String, type: ObjectType, createdObject: T) =
            AuditLogEntry(
                operation = Operation.CREATE,
                status = Status.SUCCESS,
                userId = userId,
                userRole = UserRole.USER,
                objectId = createdObject.id?.toString(),
                objectType = type,
                objectBefore = null,
                objectAfter = createdObject.toChangeLogJsonString(),
            )

        /**
         * Create an audit log entry for the update if some logged data has changed.
         *
         * @return the audit log event if there are changes. null if there are not.
         */
        fun <ID, T : HasId<ID>> updateEntry(
            userId: String,
            type: ObjectType,
            objectBefore: T,
            objectAfter: T
        ): AuditLogEntry? {
            val jsonBefore = objectBefore.toChangeLogJsonString()
            val jsonAfter = objectAfter.toChangeLogJsonString()

            return if (jsonBefore == jsonAfter) {
                null
            } else {
                AuditLogEntry(
                    operation = Operation.UPDATE,
                    status = Status.SUCCESS,
                    userId = userId,
                    userRole = UserRole.USER,
                    objectId = objectAfter.id?.toString(),
                    objectType = type,
                    objectBefore = objectBefore.toChangeLogJsonString(),
                    objectAfter = objectAfter.toChangeLogJsonString(),
                )
            }
        }
    }
}
