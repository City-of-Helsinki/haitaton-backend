package fi.hel.haitaton.hanke.logging

import fi.hel.haitaton.hanke.DatabaseStateException
import fi.hel.haitaton.hanke.HankeYhteystietoEntity
import fi.hel.haitaton.hanke.getCurrentTimeUTCAsLocalTime
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import kotlin.collections.HashSet

/**
 * Used to hold the maps of yhteystieto entities to logging entries during
 * processing, so that new yhteystietos' ids can be applied after they have
 * been saved and thus received those ids.
 *
 * Basic use:
 * 1. Create instance of this class.
 * 2. Init it (giving list of yhteystieto before doing changes to them).
 * 3. Do actions, and call addLogEntriesForEvent() for delete and update actions as they are done.
 * 4. After all actions are done, save the entities (this gives new entities their ids).
 * 5. Call addLogEntriesForNewYhteystietos() (this picks their new ids into their log entries).
 * 6. Optionally call applyIPaddresses().
 * 7. saveLogEntries() (giving the repositories)
 * 8. Forget them until someone asks something kinky about personal data (though it is good to occasionally
 *    wipe some of the older data away, because Reasons...)
 */
class YhteystietoLoggingEntryHolder {

    // To Constants?
    val PERSONAL_DATA_LOGGING_MAX_IP_LENGTH = 40

    val auditLogEntries: MutableList<AuditLogEntry> = mutableListOf()
    val changeLogEntries: MutableList<ChangeLogEntry> = mutableListOf()

    // Holds the ids of Yhteystietos that were in the Hanke before this request handling.
    val previousYTids: HashSet<Int> = hashSetOf()

    fun hasEntries() : Boolean = (auditLogEntries.size + changeLogEntries.size) > 0

    fun initWithOldYhteystietos(oldYTs: MutableList<HankeYhteystietoEntity>) {
        oldYTs.forEach {
            val ytid = it.id ?: throw DatabaseStateException("A persisted HankeYhteystietoEntity somehow missing id")
            previousYTids.add(ytid)
        }
    }

    /**
     * Creates both the audit-log and change-log entries for the given action in to this holder.
     * This function will not save them, or set IP to them; those must be done separately before
     * returning from the relevant process.
     *
     * The yhteystieto's id will be taken from the oldEntity, unless it or its id is null
     * and the newEntity is non-null, in which case it is taken from the newEntity. This
     * rule handles all cases of create, update, and delete, _if_ this function is called
     * after saving the entities for create action(s).
     *
     * No change-log entry will be made if the action is null or such that does not cause change
     * (e.g. READ). Using null action is meant for special cases or additional info.
     * READ obviously does not change anything to be given such log entry.
     * Also, a failed/blocked DELETE is not given a change log entry, as the existing data
     * remains the same, and the user's intent is obvious (to make everything go away).
     *
     * @param action can be null, in which case only the audit-log entry will be created.
     * @param failed can be null e.g. for additional info, all normal actions must provide non-null value.
     * @param description for the audit-log
     * @param oldEntity for logging the previous field values (make a clone/copy before making changes
     *              to the persisted entity, if necessary); can be null (when creating new or reading)
     * @param newEntity for logging the new state of field values; can be null (when deleting or reading)
     */
    fun addLogEntriesForEvent(
            action: Action?,
            failed: Boolean?,
            description: String,
            oldEntity: HankeYhteystietoEntity?,
            newEntity: HankeYhteystietoEntity?,
            userid: String
    ) {
        val time = getCurrentTimeUTCAsLocalTime()
        // Note, first row works for delete and update; create-action is handled
        // by the if-block.
        var yhteystietoId = oldEntity?.id
        if (yhteystietoId == null && newEntity != null)
            yhteystietoId = newEntity.id

        // Audit log (without personal data). IPs are applied in bulk later.
        val audit = AuditLogEntry(
            time, userid, null, null, null, yhteystietoId, action, failed, description)
        auditLogEntries.add(audit)

        // Data change log. Note, not made if the action is null (or is not causing a change).
        // This allows creating just an audit-log entry with this function.
        // Also, failed DELETE is not given change log entry, since all relevant data is known/obvious.
        if (action?.isChange == true && !(action == Action.DELETE && failed == true)) {
            val oldData = oldEntity?.toChangeLogJsonString()
            val newData = newEntity?.toChangeLogJsonString()
            val change = ChangeLogEntry(time, yhteystietoId, action, failed, oldData, newData)
            changeLogEntries.add(change)
        }
    }

    /**
     * Goes through the entries that were new (have null yhteystietoid in the log entry,
     * but non-null in the entity), and copies the new ids into the corresponding log entries.
     */
    fun addLogEntriesForNewYhteystietos(savedHankeYhteysTietoEntities: MutableList<HankeYhteystietoEntity>, userid: String) {
        // Go through the saved yhteystietos, filter for processing those that didn't exist
        // before, and add log entries for them now, as their id's are now known.
        // (Obviously, they all succeeded, since they have been saved already.)
        savedHankeYhteysTietoEntities
            .filter { !previousYTids.contains(it.id) }
            .forEach { newYhteystietoEntity ->
                addLogEntriesForEvent(Action.CREATE, false, "create new hanke yhteystieto", null, newYhteystietoEntity, userid)
            }
    }

    /**
     * If request context (attributes) exists, gets the IP from it and applies
     * to all the log entries currently held in this holder.
     * NOTE: very very very simplified implementation. Needs a lot of improvement.
     */
    fun applyIPaddresses() {
        val attribs = (RequestContextHolder.getRequestAttributes() as ServletRequestAttributes?) ?: return
        val request = attribs.request
        // Very simplified version for now.
        // For starters, see e.g. https://stackoverflow.com/questions/22877350/how-to-extract-ip-address-in-spring-mvc-controller-get-call
        // Combine all the various ideas into one, and note that even then it is not even half-way to
        // proper solution. Hopefully one can find a ready-made fully thought out implementation.
        var ip: String = request.getHeader("X-FORWARDED-FOR") ?: request.remoteAddr ?: return
        // Just to make sure it won't break the db if someone put something silly long in the header:
        if (ip.length > PERSONAL_DATA_LOGGING_MAX_IP_LENGTH)
            ip = ip.substring(0, PERSONAL_DATA_LOGGING_MAX_IP_LENGTH)

        auditLogEntries.forEach {
            it.ipFar = ip
            it.ipNear = ip
        }
    }

    fun saveLogEntries(
        personalDataAuditLogRepository: PersonalDataAuditLogRepository,
        personalDataChangeLogRepository: PersonalDataChangeLogRepository
    ) {
        auditLogEntries.forEach { personalDataAuditLogRepository.save(it) }
        changeLogEntries.forEach { personalDataChangeLogRepository.save(it) }
    }
}
