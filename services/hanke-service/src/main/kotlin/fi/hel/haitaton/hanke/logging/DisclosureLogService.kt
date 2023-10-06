package fi.hel.haitaton.hanke.logging

import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.ApplicationData
import fi.hel.haitaton.hanke.application.ApplicationMetaData
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.Contact
import fi.hel.haitaton.hanke.application.Customer
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.gdpr.CollectionNode
import fi.hel.haitaton.hanke.permissions.HankeKayttajaDto
import fi.hel.haitaton.hanke.toJsonString
import org.springframework.stereotype.Service

/** Special username for Allu service. */
const val ALLU_AUDIT_LOG_USERID = "Allu"

/** Special username for Helsinki Profiili. */
const val PROFIILI_AUDIT_LOG_USERID = "Helsinki Profiili"

/** Luovutusloki */
@Service
class DisclosureLogService(private val auditLogService: AuditLogService) {

    /**
     * Save disclosure log for when we are responding to a GDPR information request from Profiili.
     * Write a single disclosure log entry with the response data.
     */
    fun saveDisclosureLogsForProfiili(userId: String, gdprInfo: CollectionNode) {
        val entry = disclosureLogEntry(ObjectType.GDPR_RESPONSE, userId, gdprInfo)
        saveDisclosureLog(PROFIILI_AUDIT_LOG_USERID, UserRole.SERVICE, entry)
    }

    /**
     * Save disclosure logs for when we are sending a cable report application to Allu. Write
     * disclosure log entries for the customers and contacts in the application.
     */
    fun saveDisclosureLogsForAllu(
        applicationId: Long,
        applicationData: CableReportApplicationData,
        status: Status,
        failureDescription: String? = null
    ) {
        val dataWithId = Pair(applicationId, applicationData)
        val entries =
            auditLogEntriesForCustomers(
                listOf(dataWithId),
                ObjectType.ALLU_CUSTOMER,
                status,
                failureDescription
            ) +
                auditLogEntriesForContacts(
                    listOf(dataWithId),
                    ObjectType.ALLU_CONTACT,
                    status,
                    failureDescription
                )
        saveDisclosureLogs(ALLU_AUDIT_LOG_USERID, UserRole.SERVICE, entries)
    }

    /**
     * Save disclosure logs for when a user downloads a decision. We don't know what information is
     * inside the PDF, but we can log the meta information about the decision (or application).
     *
     * Decisions contain private information, so their reads need to be logged.
     */
    fun saveDisclosureLogsForDecision(metaData: ApplicationMetaData, userId: String) {
        val entry = disclosureLogEntry(ObjectType.DECISION, metaData.id, metaData)
        saveDisclosureLog(userId, UserRole.USER, entry)
    }

    /**
     * Save disclosure logs for when a user accesses an application. Write disclosure log entries
     * for the customers and contacts in the application.
     */
    fun saveDisclosureLogsForApplication(application: Application?, userId: String) {
        if (application == null) return
        saveDisclosureLogsForApplications(listOf(application), userId)
    }

    /**
     * Save disclosure logs for when a user accesses applications. Write disclosure log entries for
     * the customers and contacts in the applications.
     */
    fun saveDisclosureLogsForApplications(applications: List<Application>, userId: String) {
        val dataWithIds = applications.map { Pair(it.id, it.applicationData) }
        val entries =
            auditLogEntriesForCustomers(dataWithIds, ObjectType.APPLICATION_CUSTOMER) +
                auditLogEntriesForContacts(dataWithIds, ObjectType.APPLICATION_CONTACT)
        saveDisclosureLogs(userId, UserRole.USER, entries)
    }

    /**
     * Save disclosure logs for when a user accesses a hanke. Write disclosure log entries for the
     * contacts in the hanke.
     */
    fun saveDisclosureLogsForHanke(hanke: Hanke, userId: String) {
        saveDisclosureLogs(
            userId,
            UserRole.USER,
            auditLogEntriesForYhteystiedot(hanke.extractYhteystiedot())
        )
    }

    /**
     * Save disclosure logs for when a user accesses hankkeet. Write disclosure log entries for the
     * contacts in the hankkeet.
     */
    fun saveDisclosureLogsForHankkeet(hankkeet: List<Hanke>, userId: String) {
        saveDisclosureLogs(
            userId,
            UserRole.USER,
            auditLogEntriesForYhteystiedot(hankkeet.flatMap { it.extractYhteystiedot() })
        )
    }

    fun saveDisclosureLogsForHankeKayttajat(
        hankeKayttajat: List<HankeKayttajaDto>,
        userId: String
    ) {
        val entries: List<AuditLogEntry> =
            hankeKayttajat.map {
                disclosureLogEntry(ObjectType.HANKE_KAYTTAJA, it.id, it, Status.SUCCESS)
            }

        saveDisclosureLogs(userId, UserRole.USER, entries)
    }

    private fun auditLogEntriesForCustomers(
        applications: List<Pair<Long?, ApplicationData>>,
        objectType: ObjectType,
        status: Status = Status.SUCCESS,
        failureDescription: String? = null
    ): List<AuditLogEntry> =
        applications
            .flatMap { (id, data) -> extractCustomers(id, data) }
            .toSet()
            // Ignore country, since all customers have a default country atm.
            // Also, just a country can't be considered personal information.
            // I.e. check that the customer has other info besides the country.
            .filter { (_, data) -> data.copy(country = "").hasInformation() }
            // Customers don't have IDs, since they're embedded in the applications. We could use
            // the application ID here, but that would require the log reader to have deep knowledge
            // of haitaton to make sense of the objectId field.
            .map { (id, data) ->
                disclosureLogEntry(objectType, id, data, status, failureDescription)
            }

    private fun auditLogEntriesForContacts(
        applications: List<Pair<Long?, ApplicationData>>,
        objectType: ObjectType,
        status: Status = Status.SUCCESS,
        failureDescription: String? = null,
    ) =
        applications
            .flatMap { (id, data) -> extractContacts(id, data) }
            .toSet()
            .filter { (_, data) -> data.hasInformation() }
            // Contacts don't have IDs, since they're embedded in the applications. We could use the
            // application ID here, but that would require the log reader to have deep knowledge of
            // haitaton to make sense of the objectId field.
            .map { (id, data) ->
                disclosureLogEntry(objectType, id, data, status, failureDescription)
            }

    private fun extractContacts(
        applicationId: Long?,
        applicationData: ApplicationData,
    ): List<Pair<Long?, Contact>> =
        when (applicationData) {
            is CableReportApplicationData ->
                applicationData
                    .customersWithContacts()
                    .map { it.contacts }
                    .flatten()
                    .map { applicationId to it }
        }

    private fun extractCustomers(
        applicationId: Long?,
        applicationData: ApplicationData
    ): List<Pair<Long?, Customer>> =
        when (applicationData) {
            is CableReportApplicationData ->
                applicationData
                    .customersWithContacts()
                    .map { it.customer }
                    // Only personal data needs to be logged, not other types of customers.
                    .filter { it.type == CustomerType.PERSON }
                    .map { applicationId to it }
        }

    private fun auditLogEntriesForYhteystiedot(yhteystiedot: List<HankeYhteystieto>) =
        yhteystiedot.toSet().map { disclosureLogEntry(ObjectType.YHTEYSTIETO, it.id, it) }

    /** Userid and event time will be null, they will be added later. */
    private fun disclosureLogEntry(
        objectType: ObjectType,
        objectId: Any?,
        objectBefore: Any,
        status: Status = Status.SUCCESS,
        failureDescription: String? = null,
    ): AuditLogEntry =
        AuditLogEntry(
            operation = Operation.READ,
            status = status,
            failureDescription = failureDescription,
            objectType = objectType,
            objectId = objectId?.toString(),
            objectBefore = objectBefore.toJsonString()
        )

    private fun saveDisclosureLog(userId: String, userRole: UserRole, entry: AuditLogEntry) =
        auditLogService.create(entry.copy(userId = userId, userRole = userRole))

    private fun saveDisclosureLogs(
        userId: String,
        userRole: UserRole,
        entries: List<AuditLogEntry>
    ) {
        if (entries.isEmpty()) {
            return
        }

        val entities = entries.map { it.copy(userId = userId, userRole = userRole) }

        auditLogService.createAll(entities)
    }
}
