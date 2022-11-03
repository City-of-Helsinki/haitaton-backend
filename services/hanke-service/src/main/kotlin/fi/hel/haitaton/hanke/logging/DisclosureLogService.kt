package fi.hel.haitaton.hanke.logging

import com.fasterxml.jackson.module.kotlin.treeToValue
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import fi.hel.haitaton.hanke.allu.ApplicationDto
import fi.hel.haitaton.hanke.allu.CableReportApplication
import fi.hel.haitaton.hanke.allu.Contact
import fi.hel.haitaton.hanke.allu.Customer
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.toJsonString
import java.time.OffsetDateTime
import org.springframework.stereotype.Service

/** Special username for Allu service. */
const val ALLU_AUDIT_LOG_USERID = "Allu"

/** Luovutusloki */
@Service
class DisclosureLogService(private val auditLogRepository: AuditLogRepository) {

    /**
     * Save disclosure logs for when we are sending a cable report application to Allu. Write
     * disclosure log entries for the customers and contacts in the application.
     */
    fun saveDisclosureLogsForAllu(application: CableReportApplication, status: Status) {
        val entries =
            auditLogEntriesForCustomers(listOf(application), status) +
                auditLogEntriesForContacts(listOf(application), status)
        saveDisclosureLogs(ALLU_AUDIT_LOG_USERID, UserRole.SERVICE, entries)
    }

    /**
     * Save disclosure logs for when a user accesses an application. Write disclosure log entries
     * for the customers and contacts in the application.
     */
    fun saveDisclosureLogsForApplication(application: ApplicationDto?, userId: String) {
        if (application == null) return
        saveDisclosureLogsForApplications(listOf(application), userId)
    }

    /**
     * Save disclosure logs for when a user accesses applications. Write disclosure log entries for
     * the customers and contacts in the applications.
     */
    fun saveDisclosureLogsForApplications(applications: List<ApplicationDto>, userId: String) {
        val cableReportApplications = applications.mapNotNull { parseJsonApplicationData(it) }
        val entries =
            auditLogEntriesForCustomers(cableReportApplications) +
                auditLogEntriesForContacts(cableReportApplications)
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
            auditLogEntriesForYhteystiedot(extractYhteystiedot(hanke))
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
            auditLogEntriesForYhteystiedot(hankkeet.flatMap { extractYhteystiedot(it) })
        )
    }

    private fun auditLogEntriesForCustomers(
        applications: List<CableReportApplication>,
        status: Status = Status.SUCCESS
    ) =
        applications
            .flatMap { extractCustomers(it) }
            .toSet()
            // Ignore country, since all customers have a default country atm.
            // Also, just a country can't be considered personal information.
            .filter { it.copy(country = "").hasInformation() }
            // Customers don't have IDs, since they're embedded in the applications. We could use
            // the application ID here, but that would require the log reader to have deep knowledge
            // of haitaton to make sense of the objectId field.
            .map { disclosureLogEntry(ObjectType.ALLU_CUSTOMER, null, it, status) }

    private fun auditLogEntriesForContacts(
        applications: List<CableReportApplication>,
        status: Status = Status.SUCCESS
    ) =
        applications
            .flatMap { extractContacts(it) }
            .toSet()
            .filter { it.hasInformation() }
            // Contacts don't have IDs, since they're embedded in the applications. We could use the
            // application ID here, but that would require the log reader to have deep knowledge of
            // haitaton to make sense of the objectId field.
            .map { disclosureLogEntry(ObjectType.ALLU_CONTACT, null, it, status) }

    private fun parseJsonApplicationData(application: ApplicationDto?): CableReportApplication? =
        application?.applicationData?.let { OBJECT_MAPPER.treeToValue(it) }

    private fun extractContacts(application: CableReportApplication): List<Contact> =
        listOfNotNull(
                application.customerWithContacts.contacts,
                application.contractorWithContacts.contacts,
                application.representativeWithContacts?.contacts,
                application.propertyDeveloperWithContacts?.contacts,
            )
            .flatten()

    private fun extractCustomers(application: CableReportApplication): List<Customer> =
        listOfNotNull(
                application.customerWithContacts.customer,
                application.contractorWithContacts.customer,
                application.representativeWithContacts?.customer,
                application.propertyDeveloperWithContacts?.customer,
            )
            // Only personal data needs to be logged, not other types of customers.
            .filter { it.type == CustomerType.PERSON }

    fun extractYhteystiedot(hanke: Hanke) = hanke.omistajat + hanke.arvioijat + hanke.toteuttajat

    private fun auditLogEntriesForYhteystiedot(yhteystiedot: List<HankeYhteystieto>) =
        yhteystiedot.toSet().map { disclosureLogEntry(ObjectType.YHTEYSTIETO, it.id, it) }

    /** Userid and event time will be null, they will be added later. */
    private fun disclosureLogEntry(
        objectType: ObjectType,
        objectId: Any?,
        objectBefore: Any,
        status: Status = Status.SUCCESS,
    ): AuditLogEntry =
        AuditLogEntry(
            eventTime = null,
            action = Action.READ,
            status = status,
            objectType = objectType,
            objectId = objectId?.toString(),
            objectBefore = objectBefore.toJsonString()
        )

    private fun saveDisclosureLogs(
        userId: String,
        userRole: UserRole,
        entries: List<AuditLogEntry>
    ) {
        if (entries.isEmpty()) {
            return
        }

        val eventTime = OffsetDateTime.now()
        entries.forEach {
            it.userId = userId
            it.userRole = userRole
            it.eventTime = eventTime
        }

        YhteystietoLoggingEntryHolder.applyIpAddresses(entries)
        auditLogRepository.saveAll(entries)
    }
}
