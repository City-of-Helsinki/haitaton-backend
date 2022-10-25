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

/** Luovutusloki */
@Service
class DisclosureLogService(private val auditLogRepository: AuditLogRepository) {

    fun saveDisclosureLogsForApplication(application: ApplicationDto?, userId: String) {
        if (application == null) return
        saveDisclosureLogsForApplications(listOf(application), userId)
    }

    fun saveDisclosureLogsForApplications(applications: List<ApplicationDto>, userId: String) {
        val cableReportApplications = applications.mapNotNull { parseJsonApplicationData(it) }
        val entries =
            auditLogEntriesForCustomers(cableReportApplications) +
                auditLogEntriesForContacts(cableReportApplications)
        saveDisclosureLogs(userId, entries)
    }

    fun saveDisclosureLogsForHanke(hanke: Hanke, userId: String) {
        saveDisclosureLogs(userId, auditLogEntriesForYhteystiedot(extractYhteystiedot(hanke)))
    }

    fun saveDisclosureLogsForHankkeet(hankkeet: List<Hanke>, userId: String) {
        saveDisclosureLogs(
            userId,
            auditLogEntriesForYhteystiedot(hankkeet.flatMap { extractYhteystiedot(it) })
        )
    }

    private fun auditLogEntriesForCustomers(applications: List<CableReportApplication>) =
        entriesForCustomers(applications.flatMap { extractCustomers(it) }.toSet())

    private fun auditLogEntriesForContacts(applications: List<CableReportApplication>) =
        entriesForContacts(applications.flatMap { extractContacts(it) }.toSet())

    private fun entriesForCustomers(customers: Collection<Customer>) =
        // Customers don't have IDs, since they're embedded in the applications. We could use the
        // application ID here, but that would require the log reader to have deep knowledge of
        // haitaton to make sense of the objectId field.
        customers.map { disclosureLogEntry(ObjectType.ALLU_CUSTOMER, null, it) }

    private fun entriesForContacts(contacts: Collection<Contact>) =
        contacts
            .filter { !it.isBlank() }
            // Contacts don't have IDs, since they're embedded in the applications. We could use the
            // application ID here, but that would require the log reader to have deep knowledge of
            // haitaton to make sense of the objectId field.
            .map { disclosureLogEntry(ObjectType.ALLU_CONTACT, null, it) }

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

    private fun disclosureLogEntry(objectType: ObjectType, objectId: Int?, objectBefore: Any) =
        AuditLogEntry(
            eventTime = null,
            action = Action.READ,
            status = Status.SUCCESS,
            objectType = objectType,
            objectId = objectId,
            objectBefore = objectBefore.toJsonString()
        )

    private fun saveDisclosureLogs(userId: String, entries: List<AuditLogEntry>) {
        if (entries.isEmpty()) {
            return
        }
        val eventTime = OffsetDateTime.now()
        entries.forEach {
            it.userId = userId
            it.eventTime = eventTime
        }

        YhteystietoLoggingEntryHolder.applyIpAddresses(entries)
        auditLogRepository.saveAll(entries)
    }
}
