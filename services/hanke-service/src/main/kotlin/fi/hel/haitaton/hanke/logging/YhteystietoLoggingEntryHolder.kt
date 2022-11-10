package fi.hel.haitaton.hanke.logging

import fi.hel.haitaton.hanke.DatabaseStateException
import fi.hel.haitaton.hanke.HankeYhteystietoEntity
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

const val PERSONAL_DATA_LOGGING_MAX_IP_LENGTH = 40

/**
 * Used to hold the map of yhteystieto entities to logging entries during processing, so that new
 * ids of new yhteystietos can be applied after they have been saved and thus received those ids.
 *
 * Basic use:
 * 1. Create instance of this class.
 * 2. Init it (giving list of yhteystieto before doing changes to them).
 * 3. Do actions, and call addLogEntriesForEvent() for delete and update actions as they are done.
 * 4. After all actions are done, save the entities (this gives new entities their ids).
 * 5. Call addLogEntriesForNewYhteystietos() (this picks their new ids into their log entries).
 * 6. Optionally call applyIpAddresses().
 * 7. saveLogEntries() (giving the repositories)
 * 8. Forget them until a timed event sends them to Elasticsearch and removes the rows.
 */
class YhteystietoLoggingEntryHolder {

    private val auditLogEntries: MutableList<AuditLogEntry> = mutableListOf()

    // Holds the ids of Yhteystietos that were in the Hanke before this request handling.
    private val previousYhteystietoIds: HashSet<Int> = hashSetOf()

    fun hasEntries(): Boolean = auditLogEntries.isNotEmpty()

    fun idList(): String {
        return auditLogEntries.map { it.id }.joinToString()
    }

    fun initWithOldYhteystietos(oldYTs: MutableList<HankeYhteystietoEntity>) {
        oldYTs.forEach {
            val yhteystietoId =
                it.id
                    ?: throw DatabaseStateException(
                        "A persisted HankeYhteystietoEntity somehow missing id"
                    )
            previousYhteystietoIds.add(yhteystietoId)
        }
    }

    /**
     * Creates audit log entries for the given action to this holder. This function will not save
     * them, or set IP to them; those must be done separately before returning from the relevant
     * process.
     *
     * The id of the yhteystieto will be taken from the oldEntity, unless it or its id is null and
     * the newEntity is non-null, in which case it is taken from the newEntity. This rule handles
     * all cases of create, update, and delete, _if_ this function is called after saving the
     * entities for create action(s).
     *
     * For the old entity value, make a clone/copy of the persisted entity before making changes to
     * it, if necessary.
     *
     * @param action can not be null
     * @param failed can not be null
     * @param failureDescription description for the failure, if one is available
     * @param oldEntity for logging the previous field values; can be null (when creating new)
     * @param newEntity for logging the new state of field values; can be null (when deleting or
     * reading)
     * @param userId ID of the user making the API call
     */
    fun addLogEntriesForEvent(
        action: Action,
        failed: Boolean = false,
        failureDescription: String? = null,
        oldEntity: HankeYhteystietoEntity?,
        newEntity: HankeYhteystietoEntity?,
        userId: String
    ) {
        // Note, use oldEntity delete and update and newEntity for create-action.
        val yhteystietoId = oldEntity?.id ?: newEntity?.id

        val auditLogEntry =
            AuditLogEntry(
                userId = userId,
                userRole = UserRole.USER,
                action = action,
                status = if (failed) Status.FAILED else Status.SUCCESS,
                failureDescription = failureDescription,
                objectType = ObjectType.YHTEYSTIETO,
                objectId = yhteystietoId?.toString(),
                objectBefore = oldEntity?.toChangeLogJsonString(),
                objectAfter = newEntity?.toChangeLogJsonString(),
            )
        auditLogEntries.add(auditLogEntry)
    }

    /**
     * Goes through the entries that were new (have null yhteystietoId in the log entry, but
     * non-null in the entity), and copies the new ids into the corresponding log entries.
     */
    fun addLogEntriesForNewYhteystietos(
        savedHankeYhteystietoEntities: MutableList<HankeYhteystietoEntity>,
        userid: String
    ) {
        // Go through the saved yhteystietos, filter for processing those that didn't exist
        // before, and add log entries for them now, as their IDs are now known.
        // (Obviously, they all succeeded, since they have been saved already.)
        savedHankeYhteystietoEntities
            .filter { !previousYhteystietoIds.contains(it.id) }
            .forEach { newYhteystietoEntity ->
                addLogEntriesForEvent(
                    action = Action.CREATE,
                    oldEntity = null,
                    newEntity = newYhteystietoEntity,
                    userId = userid,
                )
            }
    }

    fun applyIpAddresses() {
        Companion.applyIpAddresses(auditLogEntries)
    }

    fun saveLogEntries(auditLogRepository: AuditLogRepository) {
        auditLogRepository.saveAll(auditLogEntries)
    }

    companion object {
        /**
         * If request context (attributes) exists, gets the IP from it and applies to all the log
         * entries currently held in this holder.
         *
         * TODO: very very very simplified implementation. Needs a lot of improvement.
         */
        fun applyIpAddresses(auditLogEntries: Collection<AuditLogEntry>) {
            val attribs =
                (RequestContextHolder.getRequestAttributes() as ServletRequestAttributes?) ?: return
            val request = attribs.request
            // Very simplified version for now.
            // For starters, see e.g.
            // https://stackoverflow.com/questions/22877350/how-to-extract-ip-address-in-spring-mvc-controller-get-call
            // Combine all the various ideas into one, and note that even then it is not even
            // half-way to a proper solution. Hopefully one can find a ready-made fully thought out
            // implementation.
            var ip: String = request.getHeader("X-FORWARDED-FOR") ?: request.remoteAddr ?: return
            // Just to make sure it won't break the db if someone put something silly long in the
            // header:
            if (ip.length > PERSONAL_DATA_LOGGING_MAX_IP_LENGTH)
                ip = ip.substring(0, PERSONAL_DATA_LOGGING_MAX_IP_LENGTH)

            auditLogEntries.forEach {
                it.ipFar = ip
                it.ipNear = ip
            }
        }
    }
}
