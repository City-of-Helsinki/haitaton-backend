package fi.hel.haitaton.hanke.logging

import fi.hel.haitaton.hanke.allu.Contact
import fi.hel.haitaton.hanke.allu.Customer
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.allu.CustomerWithContacts
import fi.hel.haitaton.hanke.allu.PostalAddress
import fi.hel.haitaton.hanke.allu.StreetAddress
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.factory.AuditLogEntryFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.mutate
import fi.hel.haitaton.hanke.factory.HankeFactory.withGeneratedArvioija
import fi.hel.haitaton.hanke.factory.HankeFactory.withGeneratedOmistaja
import fi.hel.haitaton.hanke.factory.HankeFactory.withGeneratedToteuttaja
import fi.hel.haitaton.hanke.factory.HankeFactory.withYhteystiedot
import fi.hel.haitaton.hanke.factory.HankeYhteystietoFactory
import fi.hel.haitaton.hanke.gdpr.CollectionNode
import fi.hel.haitaton.hanke.gdpr.StringNode
import fi.hel.haitaton.hanke.toJsonString
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class DisclosureLogServiceTest {

    private val userId = "test"

    private val auditLogService: AuditLogService = mockk(relaxed = true)
    private val disclosureLogService = DisclosureLogService(auditLogService)

    @AfterEach
    fun cleanUp() {
        // TODO: Needs newer MockK, which needs newer Spring test dependencies
        // checkUnnecessaryStub()
        confirmVerified(auditLogService)
        clearAllMocks()
    }

    @Test
    fun `saveDisclosureLogsForProfiili saves audit log of the entire info`() {
        val info =
            CollectionNode(
                "user",
                listOf(
                    StringNode("id", "4f15afe1-51dc-4015-bb66-3a536295abea"),
                    StringNode("nimi", "Teppo Testihenkilö"),
                    StringNode("sahkoposti", "teppo@example.test"),
                    StringNode("puhelin", "04012345678"),
                )
            )

        disclosureLogService.saveDisclosureLogsForProfiili(userId, info)

        val expectedObject =
            """
            |{"key":"user","children":
              |[
                |{"key":"id","value":"4f15afe1-51dc-4015-bb66-3a536295abea"},
                |{"key":"nimi","value":"Teppo Testihenkilö"},
                |{"key":"sahkoposti","value":"teppo@example.test"},
                |{"key":"puhelin","value":"04012345678"}
              |]
            |}"""
                .trimMargin()
                .replace("\n", "")
        val expectedEntries =
            listOf(
                AuditLogEntryFactory.createReadEntry(
                    userId = PROFIILI_AUDIT_LOG_USERID,
                    userRole = UserRole.SERVICE,
                    objectType = ObjectType.GDPR_RESPONSE,
                    objectId = userId,
                    objectBefore = expectedObject
                )
            )
        verify { auditLogService.createAll(match(containsAll(expectedEntries))) }
    }

    @Test
    fun `saveDisclosureLogsForHanke with hanke with no yhteystiedot doesn't do anything`() {
        val hanke = HankeFactory.create()

        disclosureLogService.saveDisclosureLogsForHanke(hanke, userId)

        verify { auditLogService wasNot Called }
    }

    @Test
    fun `saveDisclosureLogsForHanke saves audit logs for all yhteystiedot`() {
        val hanke = HankeFactory.create().withYhteystiedot()
        val expectedLogs = AuditLogEntryFactory.createReadEntriesForHanke(hanke)

        disclosureLogService.saveDisclosureLogsForHanke(hanke, userId)

        verify { auditLogService.createAll(match(containsAll(expectedLogs))) }
    }

    @Test
    fun `saveDisclosureLogsForHanke saves identical audit logs only once`() {
        val yhteystieto = HankeYhteystietoFactory.createDifferentiated(1)
        yhteystieto.id = 1
        val hanke =
            HankeFactory.create().mutate {
                it.omistajat = mutableListOf(yhteystieto)
                it.arvioijat = mutableListOf(yhteystieto)
                it.toteuttajat = mutableListOf(yhteystieto)
            }
        val expectedLogs =
            listOf(AuditLogEntryFactory.createReadEntry(objectBefore = yhteystieto.toJsonString()))

        disclosureLogService.saveDisclosureLogsForHanke(hanke, userId)

        verify { auditLogService.createAll(match(containsAll(expectedLogs))) }
    }

    @Test
    fun `saveDisclosureLogsForHankkeet without hankkeet does nothing`() {
        disclosureLogService.saveDisclosureLogsForHankkeet(listOf(), userId)

        verify { auditLogService wasNot Called }
    }

    @Test
    fun `saveDisclosureLogsForHankkeet with hankkeet without yhteystiedot does nothing`() {
        val hankkeet = listOf(HankeFactory.create(), HankeFactory.create())

        disclosureLogService.saveDisclosureLogsForHankkeet(hankkeet, userId)

        verify { auditLogService wasNot Called }
    }

    @Test
    fun `saveDisclosureLogsForHankkeet saves audit logs for all yhteystiedot in all hankkeet`() {
        val hankkeet =
            listOf(
                HankeFactory.create().withYhteystiedot(),
                HankeFactory.create()
                    .withGeneratedOmistaja(4)
                    .withGeneratedArvioija(5)
                    .withGeneratedToteuttaja(6)
            )
        val expectedLogs = hankkeet.flatMap { AuditLogEntryFactory.createReadEntriesForHanke(it) }

        disclosureLogService.saveDisclosureLogsForHankkeet(hankkeet, userId)

        verify { auditLogService.createAll(match(containsAll(expectedLogs))) }
    }

    @Test
    fun `saveDisclosureLogsForHankkeet saves identical audit logs only once`() {
        val hankkeet =
            listOf(
                HankeFactory.create().withYhteystiedot(),
                HankeFactory.create().withYhteystiedot()
            )
        val expectedLogs = AuditLogEntryFactory.createReadEntriesForHanke(hankkeet[0])

        disclosureLogService.saveDisclosureLogsForHankkeet(hankkeet, userId)

        verify { auditLogService.createAll(match(containsAll(expectedLogs))) }
    }

    @Test
    fun `saveDisclosureLogsForApplication with null application does nothing`() {
        disclosureLogService.saveDisclosureLogsForApplication(null, userId)

        verify { auditLogService wasNot Called }
    }

    @Test
    fun `saveDisclosureLogsForApplication with company customers and no contacts does nothing`() {
        val customerWithoutContacts =
            CustomerWithContacts(AlluDataFactory.createCompanyCustomer(name = "First"), listOf())
        val contractorWithoutContacts =
            CustomerWithContacts(AlluDataFactory.createCompanyCustomer(name = "Second"), listOf())
        val application =
            AlluDataFactory.createApplication(
                applicationData =
                    AlluDataFactory.createCableReportApplicationData(
                        customerWithContacts = customerWithoutContacts,
                        contractorWithContacts = contractorWithoutContacts
                    )
            )

        disclosureLogService.saveDisclosureLogsForApplication(application, userId)

        verify { auditLogService wasNot Called }
    }

    @Test
    fun `saveDisclosureLogsForApplication doesn't save entries for blank contacts`() {
        val customerWithoutContacts =
            CustomerWithContacts(AlluDataFactory.createCompanyCustomer(name = "First"), listOf())
        val contractorWithoutContacts =
            CustomerWithContacts(
                AlluDataFactory.createCompanyCustomer(name = "Second"),
                listOf(Contact("", PostalAddress(StreetAddress(""), "", ""), "", ""))
            )
        val application =
            AlluDataFactory.createApplication(
                applicationData =
                    AlluDataFactory.createCableReportApplicationData(
                        customerWithContacts = customerWithoutContacts,
                        contractorWithContacts = contractorWithoutContacts
                    )
            )

        disclosureLogService.saveDisclosureLogsForApplication(application, userId)

        verify { auditLogService wasNot Called }
    }

    @Test
    fun `saveDisclosureLogsForApplication doesn't save entries for blank customers`() {
        val blankCustomer =
            Customer(type = CustomerType.PERSON, "", "", null, "", "", "", "", "", "")
        val blankCustomerWithCountry =
            Customer(type = CustomerType.PERSON, "", "FI", null, "", "", "", "", "", "")
        val customerWithoutContacts = CustomerWithContacts(blankCustomer, listOf())
        val contractorWithoutContacts = CustomerWithContacts(blankCustomerWithCountry, listOf())
        val application =
            AlluDataFactory.createApplication(
                applicationData =
                    AlluDataFactory.createCableReportApplicationData(
                        customerWithContacts = customerWithoutContacts,
                        contractorWithContacts = contractorWithoutContacts
                    )
            )

        disclosureLogService.saveDisclosureLogsForApplication(application, userId)

        verify { auditLogService wasNot Called }
    }

    @Test
    fun `saveDisclosureLogsForApplication with identical person customers logs only once`() {
        val customerWithoutContacts =
            CustomerWithContacts(AlluDataFactory.createPersonCustomer(), listOf())
        val contractorWithoutContacts =
            CustomerWithContacts(AlluDataFactory.createPersonCustomer(), listOf())
        val application =
            AlluDataFactory.createApplication(
                applicationData =
                    AlluDataFactory.createCableReportApplicationData(
                        customerWithContacts = customerWithoutContacts,
                        contractorWithContacts = contractorWithoutContacts
                    )
            )
        val expectedLogs =
            listOf(
                AuditLogEntryFactory.createReadEntryForCustomer(customerWithoutContacts.customer)
            )

        disclosureLogService.saveDisclosureLogsForApplication(application, userId)

        verify { auditLogService.createAll(match(containsAll(expectedLogs))) }
    }

    @Test
    fun `saveDisclosureLogsForApplication with contacts logs contacts`() {
        val cableReportApplication = AlluDataFactory.createCableReportApplicationData()
        val contact = cableReportApplication.customerWithContacts.contacts[0]
        val expectedLogs = listOf(AuditLogEntryFactory.createReadEntryForContact(contact))
        val application =
            AlluDataFactory.createApplication(applicationData = cableReportApplication)

        disclosureLogService.saveDisclosureLogsForApplication(application, userId)

        verify { auditLogService.createAll(match(containsAll(expectedLogs))) }
    }

    @ParameterizedTest(name = "{displayName}({arguments})")
    @EnumSource(Status::class)
    fun `saveDisclosureLogsForAllu saves logs with the given status`(expectedStatus: Status) {
        val cableReportApplication = AlluDataFactory.createCableReportApplicationData()
        val contact = cableReportApplication.customerWithContacts.contacts[0]
        val expectedLogs =
            listOf(
                AuditLogEntryFactory.createReadEntryForContact(contact)
                    .copy(
                        status = expectedStatus,
                        userId = ALLU_AUDIT_LOG_USERID,
                        userRole = UserRole.SERVICE,
                    )
            )

        disclosureLogService.saveDisclosureLogsForAllu(cableReportApplication, expectedStatus)

        verify { auditLogService.createAll(match(containsAll(expectedLogs))) }
    }

    @Test
    fun `saveDisclosureLogsForApplications logs customers and contacts from all applications while ignoring duplicates`() {
        val contacts = (1..8).map { AlluDataFactory.createContact(name = "Contact $it") }
        val customers = (1..4).map { AlluDataFactory.createPersonCustomer(name = "Customer $it") }
        val expectedLogs =
            (contacts.map { AuditLogEntryFactory.createReadEntryForContact(it) } +
                customers.map { AuditLogEntryFactory.createReadEntryForCustomer(it) })
        assertEquals(12, expectedLogs.size)
        val customersWithContacts =
            (0..3).map { i ->
                CustomerWithContacts(
                    customers[i],
                    listOf(contacts[i], contacts[i + 4], contacts[0])
                )
            }
        val applications =
            listOf(
                    AlluDataFactory.createCableReportApplicationData(
                        customerWithContacts = customersWithContacts[0],
                        contractorWithContacts = customersWithContacts[1],
                    ),
                    AlluDataFactory.createCableReportApplicationData(
                        customerWithContacts = customersWithContacts[2],
                        contractorWithContacts = customersWithContacts[3],
                    )
                )
                .map { AlluDataFactory.createApplication(applicationData = it) }

        disclosureLogService.saveDisclosureLogsForApplications(applications, userId)

        verify { auditLogService.createAll(match(containsAll(expectedLogs))) }
    }

    @Test
    fun `saveDisclosureLogsForApplications with company customers and no contacts does nothing`() {
        disclosureLogService.saveDisclosureLogsForApplications(listOf(), userId)

        verify { auditLogService wasNot Called }
    }

    private fun containsAll(
        expectedLogs: List<AuditLogEntry>,
    ): (List<AuditLogEntry>) -> Boolean = { entries -> entries equalsIgnoreOrder expectedLogs }

    private infix fun <T> List<T>.equalsIgnoreOrder(other: List<T>): Boolean =
        this.size == other.size && this.toSet() == other.toSet()
}
