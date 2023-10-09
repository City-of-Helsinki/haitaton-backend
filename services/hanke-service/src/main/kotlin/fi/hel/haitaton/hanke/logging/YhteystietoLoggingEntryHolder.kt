package fi.hel.haitaton.hanke.logging

import fi.hel.haitaton.hanke.DatabaseStateException
import fi.hel.haitaton.hanke.HankeYhteystietoEntity
import fi.hel.haitaton.hanke.toChangeLogJsonString

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

    private var auditLogEntries: MutableList<AuditLogEntry> = mutableListOf()

    // Holds the ids of Yhteystietos that were in the Hanke before this request handling.
    private val previousYhteystietoIds: HashSet<Int> = hashSetOf()

    fun hasEntries(): Boolean = auditLogEntries.isNotEmpty()

    fun objectIds(): String {
        return auditLogEntries.map { it.objectId }.joinToString()
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
     * Creates audit log entries for the given operation to this holder. This function will not save
     * them, or set IP to them; those must be done separately before returning from the relevant
     * process.
     *
     * The id of the yhteystieto will be taken from the oldEntity, unless it or its id is null and
     * the newEntity is non-null, in which case it is taken from the newEntity. This rule handles
     * all cases of create, update, and delete, _if_ this function is called after saving the
     * entities for create operation(s).
     *
     * For the old entity value, make a clone/copy of the persisted entity before making changes to
     * it, if necessary.
     *
     * @param operation can not be null
     * @param failed can not be null
     * @param failureDescription description for the failure, if one is available
     * @param oldEntity for logging the previous field values; can be null (when creating new)
     * @param newEntity for logging the new state of field values; can be null (when deleting or
     * reading)
     * @param userId ID of the user making the API call
     */
    fun addLogEntriesForEvent(
        operation: Operation,
        failed: Boolean = false,
        failureDescription: String? = null,
        oldEntity: HankeYhteystietoEntity?,
        newEntity: HankeYhteystietoEntity?,
        userId: String
    ) {
        // Note, use oldEntity for delete and update and newEntity for create-operation.
        val yhteystietoId = oldEntity?.id ?: newEntity?.id

        val auditLogEntry =
            AuditLogEntry(
                userId = userId,
                userRole = UserRole.USER,
                operation = operation,
                status = if (failed) Status.FAILED else Status.SUCCESS,
                failureDescription = failureDescription,
                objectType = ObjectType.YHTEYSTIETO,
                objectId = yhteystietoId!!.toString(),
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
                    operation = Operation.CREATE,
                    oldEntity = null,
                    newEntity = newYhteystietoEntity,
                    userId = userid,
                )
            }
    }

    fun saveLogEntries(auditLogService: AuditLogService) {
        auditLogService.createAll(auditLogEntries)
    }
}
