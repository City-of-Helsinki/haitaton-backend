package fi.hel.haitaton.hanke.logging

import com.fasterxml.jackson.annotation.JsonUnwrapped
import fi.hel.haitaton.hanke.allu.AlluApplicationData
import fi.hel.haitaton.hanke.allu.Contact as AlluContact
import fi.hel.haitaton.hanke.allu.Customer as AlluCustomer
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.domain.HankeYhteystieto
import fi.hel.haitaton.hanke.gdpr.CollectionNode
import fi.hel.haitaton.hanke.hakemus.ApplicationContactType
import fi.hel.haitaton.hanke.hakemus.ContactResponse
import fi.hel.haitaton.hanke.hakemus.CustomerResponse
import fi.hel.haitaton.hanke.hakemus.HakemusDataResponse
import fi.hel.haitaton.hanke.hakemus.HakemusMetaData
import fi.hel.haitaton.hanke.hakemus.HakemusResponse
import fi.hel.haitaton.hanke.hakemus.InvoicingCustomerResponse
import fi.hel.haitaton.hanke.hakemus.JohtoselvitysHakemusDataResponse
import fi.hel.haitaton.hanke.hakemus.KaivuilmoitusDataResponse
import fi.hel.haitaton.hanke.paatos.PaatosMetadata
import fi.hel.haitaton.hanke.permissions.HankeKayttajaDto
import fi.hel.haitaton.hanke.profiili.Names
import fi.hel.haitaton.hanke.taydennys.TaydennysResponse
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
    fun saveForProfiili(gdprInfo: CollectionNode, userId: String) {
        val entry = disclosureLogEntry(ObjectType.GDPR_RESPONSE, userId, gdprInfo)
        saveDisclosureLog(PROFIILI_AUDIT_LOG_USERID, UserRole.SERVICE, entry)
    }

    /**
     * Save disclosure log for when we are reading the verified name from Profiili. Write a single
     * disclosure log entry with user's names.
     */
    fun saveForProfiiliNimi(names: Names, userId: String) {
        val entry = disclosureLogEntry(ObjectType.PROFIILI_NIMI, userId, names)
        saveDisclosureLog(userId, UserRole.USER, entry)
    }

    /**
     * Save disclosure logs for when we are sending a cable report application to Allu. Write
     * disclosure log entries for the customers and contacts in the application.
     */
    fun saveForAllu(
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
                applicationId, applicationData, status, failureDescription)
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
    fun saveForCableReport(metaData: HakemusMetaData, userId: String) {
        val entry = disclosureLogEntry(ObjectType.CABLE_REPORT, metaData.id, metaData)
        saveDisclosureLog(userId, UserRole.USER, entry)
    }

    /**
     * Save disclosure logs for when a user downloads a decision or supervision document. We don't
     * know what information is inside the PDF, but we can log the meta information about the
     * decision.
     *
     * Decisions contain private information, so their reads need to be logged.
     */
    fun saveForPaatos(metaData: PaatosMetadata, userId: String) {
        val entry = disclosureLogEntry(ObjectType.PAATOS, metaData.id, metaData)
        saveDisclosureLog(userId, UserRole.USER, entry)
    }

    /**
     * Save disclosure logs for when a user accesses an application. Write disclosure log entries
     * for the customers and contacts in the application.
     */
    fun saveForHakemusResponse(hakemusResponse: HakemusResponse, userId: String) {
        val entries =
            auditLogEntriesForHakemusDataResponseCustomers(
                hakemusResponse.id,
                hakemusResponse.applicationData,
                ObjectType.APPLICATION_CUSTOMER) +
                auditLogEntriesForHakemusDataResponseContacts(
                    hakemusResponse.id,
                    hakemusResponse.applicationData,
                    ObjectType.APPLICATION_CONTACT)

        saveDisclosureLogs(userId, UserRole.USER, entries)
    }

    /**
     * Save disclosure logs for when a user accesses a taydennys. Write disclosure log entries for
     * the customers and contacts in the taydennys.
     */
    fun saveForTaydennys(taydennysResponse: TaydennysResponse, currentUserId: String) {
        val entries =
            auditLogEntriesForHakemusDataResponseCustomers(
                taydennysResponse.id,
                taydennysResponse.applicationData,
                ObjectType.TAYDENNYS_CUSTOMER) +
                auditLogEntriesForHakemusDataResponseContacts(
                    taydennysResponse.id,
                    taydennysResponse.applicationData,
                    ObjectType.TAYDENNYS_CONTACT)
        saveDisclosureLogs(currentUserId, UserRole.USER, entries)
    }

    /**
     * Save disclosure logs for when a user accesses a hanke. Write disclosure log entries for the
     * contacts in the hanke.
     */
    fun saveForHanke(hanke: Hanke, userId: String) {
        saveDisclosureLogs(
            userId,
            UserRole.USER,
            auditLogEntriesForYhteystiedot(hanke.extractYhteystiedot()),
        )
    }

    /**
     * Save disclosure logs for when a user accesses hankkeet. Write disclosure log entries for the
     * contacts in the hankkeet.
     */
    fun saveForHankkeet(hankkeet: List<Hanke>, userId: String) {
        saveDisclosureLogs(
            userId,
            UserRole.USER,
            auditLogEntriesForYhteystiedot(hankkeet.flatMap { it.extractYhteystiedot() }),
        )
    }

    fun saveForHankeKayttaja(hankeKayttaja: HankeKayttajaDto, userId: String) {
        saveForHankeKayttajat(listOf(hankeKayttaja), userId)
    }

    fun saveForHankeKayttajat(hankeKayttajat: List<HankeKayttajaDto>, userId: String) {
        val entries: List<AuditLogEntry> =
            hankeKayttajat.map {
                disclosureLogEntry(ObjectType.HANKE_KAYTTAJA, it.id, it, Status.SUCCESS)
            }

        saveDisclosureLogs(userId, UserRole.USER, entries)
    }

    private fun auditLogEntriesForCustomers(
        applicationId: Long,
        applicationData: AlluApplicationData,
        status: Status,
        failureDescription: String?,
    ): List<AuditLogEntry> =
        extractCustomers(applicationData).toSet().map { customer ->
            disclosureLogEntry(
                ObjectType.ALLU_CUSTOMER, applicationId, customer, status, failureDescription)
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
            ObjectType.ALLU_CUSTOMER, applicationId, customer, status, failureDescription)
    }

    private fun <T : Any> auditLogEntriesForHakemusDataResponseCustomers(
        objectId: T,
        hakemusDataResponse: HakemusDataResponse,
        objectType: ObjectType,
        status: Status = Status.SUCCESS,
        failureDescription: String? = null
    ): List<AuditLogEntry> =
        extractHakemusDataResponseCustomers(hakemusDataResponse).toSet().map { customer ->
            disclosureLogEntry(objectType, objectId, customer, status, failureDescription)
        } +
            (extractHakemusDataResponseInvoicingCustomer(hakemusDataResponse)?.let {
                listOf(disclosureLogEntry(objectType, objectId, it, status, failureDescription))
            } ?: emptyList())

    private fun auditLogEntriesForContacts(
        applicationId: Long,
        applicationData: AlluApplicationData,
        status: Status,
        failureDescription: String?,
    ): List<AuditLogEntry> =
        extractContacts(applicationData).toSet().map { contact ->
            disclosureLogEntry(
                ObjectType.ALLU_CONTACT, applicationId, contact, status, failureDescription)
        }

    private fun <T : Any> auditLogEntriesForHakemusDataResponseContacts(
        objectId: T,
        hakemusDataResponse: HakemusDataResponse,
        objectType: ObjectType,
        status: Status = Status.SUCCESS,
        failureDescription: String? = null,
    ): List<AuditLogEntry> =
        extractHakemusDataResponseContacts(hakemusDataResponse).toSet().map { contact ->
            disclosureLogEntry(objectType, objectId, contact, status, failureDescription)
        }

    private fun extractContacts(applicationData: AlluApplicationData): List<AlluContactWithRole> =
        applicationData
            .customersByRole()
            .flatMap { (role, customer) -> customer.contacts.map { AlluContactWithRole(role, it) } }
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

    private fun extractHakemusDataResponseInvoicingCustomer(
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
            objectBefore = objectBefore.toJsonString(),
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
