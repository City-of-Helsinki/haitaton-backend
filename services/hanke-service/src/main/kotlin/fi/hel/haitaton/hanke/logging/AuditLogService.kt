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

        fun <ID, T : HasId<ID>> deleteEntry(userId: String, type: ObjectType, objectBefore: T) =
            AuditLogEntry(
                operation = Operation.DELETE,
                status = Status.SUCCESS,
                userId = userId,
                userRole = UserRole.USER,
                objectId = objectBefore.id?.toString(),
                objectType = type,
                objectBefore = objectBefore.toChangeLogJsonString(),
                objectAfter = null,
            )
    }
}
