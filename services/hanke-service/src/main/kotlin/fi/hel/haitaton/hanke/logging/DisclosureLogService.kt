package fi.hel.haitaton.hanke.logging

import com.fasterxml.jackson.annotation.JsonUnwrapped
import fi.hel.haitaton.hanke.allu.AlluApplicationData
import fi.hel.haitaton.hanke.allu.Contact as AlluContact
import fi.hel.haitaton.hanke.allu.Customer as AlluCustomer
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.Application
import fi.hel.haitaton.hanke.application.ApplicationContactType
import fi.hel.haitaton.hanke.application.ApplicationData
import fi.hel.haitaton.hanke.application.ApplicationMetaData
import fi.hel.haitaton.hanke.application.Contact
import fi.hel.haitaton.hanke.application.Customer
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.gdpr.CollectionNode
import fi.hel.haitaton.hanke.hakemus.ContactResponse
import fi.hel.haitaton.hanke.hakemus.CustomerResponse
import fi.hel.haitaton.hanke.hakemus.HakemusDataResponse
import fi.hel.haitaton.hanke.hakemus.HakemusResponse
import fi.hel.haitaton.hanke.hakemus.InvoicingCustomerResponse
import fi.hel.haitaton.hanke.hakemus.JohtoselvitysHakemusDataResponse
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusDataResponse
import fi.hel.haitaton.hanke.permissions.HankeKayttajaDto
import fi.hel.haitaton.hanke.profiili.Names
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
     * Save disclosure log for when we are reading the verfied name from Profiili. Write a single
     * disclosure log entry with user's names.
     */
    fun saveDisclosureLogsForProfiiliNimi(userId: String, names: Names) {
        val entry = disclosureLogEntry(ObjectType.PROFIILI_NIMI, userId, names)
        saveDisclosureLog(userId, UserRole.USER, entry)
    }

    /**
     * Save disclosure logs for when we are sending a cable report application to Allu. Write
     * disclosure log entries for the customers and contacts in the application.
     */
    fun saveDisclosureLogsForAllu(
        applicationId: Long,
        applicationData: AlluApplicationData,
        status: Status,
        failureDescription: String? = null
    ) {
        val customerEntries =
            auditLogEntriesForCustomers(applicationId, applicationData, status, failureDescription)
        val contactEntries =
            auditLogEntriesForContacts(applicationId, applicationData, status, failureDescription)
        val invoicingEntry =
            auditLogEntryForInvoicingCustomer(
                applicationId,
                applicationData,
                status,
                failureDescription
            )
        val entries = (customerEntries + contactEntries + invoicingEntry).filterNotNull()

        saveDisclosureLogs(ALLU_AUDIT_LOG_USERID, UserRole.SERVICE, entries)
    }

    /**
     * Save disclosure logs for when a user downloads a cable report. We don't know what information
     * is inside the PDF, but we can log the meta information about the cable report (or
     * application).
     *
     * Cable reports contain private information, so their reads need to be logged.
     */
    fun saveDisclosureLogsForCableReport(metaData: ApplicationMetaData, userId: String) {
        val entry = disclosureLogEntry(ObjectType.CABLE_REPORT, metaData.id, metaData)
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
        val entries =
            auditLogEntriesForCustomers(applications) + auditLogEntriesForContacts(applications)
        saveDisclosureLogs(userId, UserRole.USER, entries)
    }

    /**
     * Save disclosure logs for when a user accesses an application. Write disclosure log entries
     * for the customers and contacts in the application.
     */
    fun saveDisclosureLogsForHakemusResponse(hakemusResponse: HakemusResponse, userId: String) {
        saveDisclosureLogsForHakemusResponses(listOf(hakemusResponse), userId)
    }

    /**
     * Save disclosure logs for when a user accesses applications. Write disclosure log entries for
     * the customers and contacts in the applications.
     */
    fun saveDisclosureLogsForHakemusResponses(
        hakemusResponses: List<HakemusResponse>,
        userId: String
    ) {
        val entries =
            auditLogEntriesForHakemusResponseCustomers(hakemusResponses) +
                auditLogEntriesForHakemusResponseContacts(hakemusResponses)
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

    fun saveDisclosureLogsForHankeKayttaja(hankeKayttaja: HankeKayttajaDto, userId: String) {
        saveDisclosureLogsForHankeKayttajat(listOf(hankeKayttaja), userId)
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
        applicationId: Long,
        applicationData: ApplicationData,
        status: Status = Status.SUCCESS,
        failureDescription: String? = null
    ): List<AuditLogEntry> =
        extractCustomers(applicationData).toSet().map { customer ->
            disclosureLogEntry(
                ObjectType.APPLICATION_CUSTOMER,
                applicationId,
                customer,
                status,
                failureDescription
            )
        }

    private fun auditLogEntriesForCustomers(
        applicationId: Long,
        applicationData: AlluApplicationData,
        status: Status,
        failureDescription: String?,
    ): List<AuditLogEntry> =
        extractCustomers(applicationData).toSet().map { customer ->
            disclosureLogEntry(
                ObjectType.ALLU_CUSTOMER,
                applicationId,
                customer,
                status,
                failureDescription
            )
        }

    private fun auditLogEntryForInvoicingCustomer(
        applicationId: Long,
        applicationData: AlluApplicationData,
        status: Status = Status.SUCCESS,
        failureDescription: String? = null,
    ): AuditLogEntry? {
        val invoicingCustomer = applicationData.invoicingCustomer ?: return null
        if (invoicingCustomer.type != CustomerType.PERSON) return null

        val customer = AlluMetaCustomerWithRole(MetaCustomerType.INVOICING, invoicingCustomer)

        return disclosureLogEntry(
            ObjectType.ALLU_CUSTOMER,
            applicationId,
            customer,
            status,
            failureDescription
        )
    }

    private fun auditLogEntriesForHakemusDataResponseCustomers(
        applicationId: Long,
        hakemusDataResponse: HakemusDataResponse,
        objectType: ObjectType,
        status: Status = Status.SUCCESS,
        failureDescription: String? = null
    ): List<AuditLogEntry> =
        extractHakemusDataResponseCustomers(hakemusDataResponse).toSet().map { customer ->
            disclosureLogEntry(objectType, applicationId, customer, status, failureDescription)
        } +
            (extractHakemusDataResponseInvoivingCustomer(hakemusDataResponse)?.let {
                listOf(
                    disclosureLogEntry(objectType, applicationId, it, status, failureDescription)
                )
            } ?: emptyList())

    private fun auditLogEntriesForCustomers(
        applications: List<Application>,
    ): Set<AuditLogEntry> =
        applications.flatMap { auditLogEntriesForCustomers(it.id!!, it.applicationData) }.toSet()

    private fun auditLogEntriesForHakemusResponseCustomers(
        hakemusResponses: List<HakemusResponse>,
        objectType: ObjectType = ObjectType.APPLICATION_CUSTOMER,
    ): Set<AuditLogEntry> =
        hakemusResponses
            .flatMap {
                auditLogEntriesForHakemusDataResponseCustomers(
                    it.id,
                    it.applicationData,
                    objectType
                )
            }
            .toSet()

    private fun auditLogEntriesForContacts(
        applicationId: Long,
        applicationData: ApplicationData,
        status: Status = Status.SUCCESS,
        failureDescription: String? = null,
    ): List<AuditLogEntry> =
        extractContacts(applicationData).toSet().map { contact ->
            disclosureLogEntry(
                ObjectType.APPLICATION_CONTACT,
                applicationId,
                contact,
                status,
                failureDescription
            )
        }

    private fun auditLogEntriesForContacts(
        applicationId: Long,
        applicationData: AlluApplicationData,
        status: Status,
        failureDescription: String?,
    ): List<AuditLogEntry> =
        extractContacts(applicationData).toSet().map { contact ->
            disclosureLogEntry(
                ObjectType.ALLU_CONTACT,
                applicationId,
                contact,
                status,
                failureDescription
            )
        }

    private fun auditLogEntriesForContacts(applications: List<Application>): Set<AuditLogEntry> =
        applications.flatMap { auditLogEntriesForContacts(it.id!!, it.applicationData) }.toSet()

    private fun auditLogEntriesForHakemusDataResponseContacts(
        applicationId: Long,
        hakemusDataResponse: HakemusDataResponse,
        objectType: ObjectType,
        status: Status = Status.SUCCESS,
        failureDescription: String? = null,
    ): List<AuditLogEntry> =
        extractHakemusDataResponseContacts(hakemusDataResponse).toSet().map { contact ->
            disclosureLogEntry(objectType, applicationId, contact, status, failureDescription)
        }

    private fun auditLogEntriesForHakemusResponseContacts(
        applications: List<HakemusResponse>,
        objectType: ObjectType = ObjectType.APPLICATION_CONTACT,
    ): Set<AuditLogEntry> =
        applications
            .flatMap {
                auditLogEntriesForHakemusDataResponseContacts(it.id, it.applicationData, objectType)
            }
            .toSet()

    private fun extractContacts(applicationData: AlluApplicationData): List<AlluContactWithRole> =
        applicationData
            .customersByRole()
            .flatMap { (role, customer) -> customer.contacts.map { AlluContactWithRole(role, it) } }
            .filter { it.contact.hasInformation() }

    private fun extractContacts(applicationData: ApplicationData): List<ContactWithRole> =
        applicationData
            .customersByRole()
            .flatMap { (role, customer) -> customer.contacts.map { ContactWithRole(role, it) } }
            .filter { it.contact.hasInformation() }

    private fun extractHakemusDataResponseContacts(
        hakemusDataResponse: HakemusDataResponse
    ): List<ContactResponseWithRole> =
        hakemusDataResponse
            .customersByRole()
            .flatMap { (role, customer) ->
                customer.contacts.map { ContactResponseWithRole(role, it) }
            }
            .filter { it.contact.hasInformation() }

    private fun extractCustomers(applicationData: ApplicationData): List<CustomerWithRole> =
        applicationData
            .customersByRole()
            .map { (role, customer) -> CustomerWithRole(role, customer.customer) }
            // Only personal data needs to be logged, not other types of customers.
            .filter { it.customer.type == CustomerType.PERSON }
            .filter { it.customer.hasPersonalInformation() }

    private fun extractCustomers(applicationData: AlluApplicationData): List<AlluCustomerWithRole> =
        applicationData
            .customersByRole()
            .map { (role, customer) -> AlluCustomerWithRole(role, customer.customer) }
            // Only personal data needs to be logged, not other types of customers.
            .filter { it.customer.type == CustomerType.PERSON }
            .filter { it.customer.hasPersonalInformation() }

    private fun extractHakemusDataResponseCustomers(
        hakemusDataResponse: HakemusDataResponse
    ): List<CustomerResponseWithRole> =
        hakemusDataResponse
            .customersByRole()
            .map { (role, customer) -> CustomerResponseWithRole(role, customer.customer) }
            // Only personal data needs to be logged, not other types of customers.
            .filter { it.customer.type == CustomerType.PERSON }
            .filter { it.customer.hasPersonalInformation() }

    private fun extractHakemusDataResponseInvoivingCustomer(
        hakemusDataResponse: HakemusDataResponse
    ): InvoicingCustomerResponse? =
        when (hakemusDataResponse) {
            is JohtoselvitysHakemusDataResponse -> null
            is KaivuilmoitusDataResponse ->
                hakemusDataResponse.invoicingCustomer?.takeIf {
                    it.type == CustomerType.PERSON && it.hasPersonalInformation()
                }
        }

    private fun auditLogEntriesForYhteystiedot(yhteystiedot: List<HankeYhteystieto>) =
        yhteystiedot.toSet().map { disclosureLogEntry(ObjectType.YHTEYSTIETO, it.id!!, it) }

    /** Userid and event time will be null, they will be added later. */
    private fun disclosureLogEntry(
        objectType: ObjectType,
        objectId: Any,
        objectBefore: Any,
        status: Status = Status.SUCCESS,
        failureDescription: String? = null,
    ): AuditLogEntry =
        AuditLogEntry(
            operation = Operation.READ,
            status = status,
            failureDescription = failureDescription,
            objectType = objectType,
            objectId = objectId.toString(),
            objectBefore = objectBefore.toJsonString()
        )

    private fun saveDisclosureLog(userId: String, userRole: UserRole, entry: AuditLogEntry) =
        auditLogService.create(entry.copy(userId = userId, userRole = userRole))

    private fun saveDisclosureLogs(
        userId: String,
        userRole: UserRole,
        entries: Collection<AuditLogEntry>
    ) {
        if (entries.isEmpty()) {
            return
        }

        val entities = entries.map { it.copy(userId = userId, userRole = userRole) }

        auditLogService.createAll(entities)
    }
}

data class CustomerWithRole(
    val role: ApplicationContactType,
    @JsonUnwrapped val customer: Customer,
)

data class AlluCustomerWithRole(
    val role: ApplicationContactType,
    @JsonUnwrapped val customer: AlluCustomer,
)

data class AlluMetaCustomerWithRole(
    val role: MetaCustomerType,
    @JsonUnwrapped val customer: AlluCustomer,
)

data class CustomerResponseWithRole(
    val role: ApplicationContactType,
    @JsonUnwrapped val customer: CustomerResponse,
)

data class ContactWithRole(
    val role: ApplicationContactType,
    @JsonUnwrapped val contact: Contact,
)

data class AlluContactWithRole(
    val role: ApplicationContactType,
    @JsonUnwrapped val contact: AlluContact,
)

data class ContactResponseWithRole(
    val role: ApplicationContactType,
    @JsonUnwrapped val contact: ContactResponse,
)

enum class MetaCustomerType {
    INVOICING
}
