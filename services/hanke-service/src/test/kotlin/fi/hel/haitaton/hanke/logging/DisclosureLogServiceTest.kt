package fi.hel.haitaton.hanke.logging

import fi.hel.haitaton.hanke.OBJECT_MAPPER
import fi.hel.haitaton.hanke.allu.ApplicationDto
import fi.hel.haitaton.hanke.allu.ApplicationType
import fi.hel.haitaton.hanke.allu.CableReportApplication
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
import fi.hel.haitaton.hanke.toJsonString
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

internal class DisclosureLogServiceTest {

    private val userId = "test"

    private val auditLogRepository: AuditLogRepository = mockk()
    private val disclosureLogService = DisclosureLogService(auditLogRepository)

    @BeforeEach
    fun clearMockks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkStubs() {
        // TODO: Needs newer MockK, which needs newer Spring test dependencies
        // checkUnnecessaryStub()
        confirmVerified()
    }

    @Test
    fun `saveDisclosureLogsForHanke with hanke with no yhteystiedot doesn't do anything`() {
        val hanke = HankeFactory.create()

        disclosureLogService.saveDisclosureLogsForHanke(hanke, userId)

        verify { auditLogRepository wasNot Called }
    }

    @Test
    fun `saveDisclosureLogsForHanke saves audit logs for all yhteystiedot`() {
        val hanke = HankeFactory.create().withYhteystiedot()
        val expectedLogs = AuditLogEntryFactory.createReadEntriesForHanke(hanke)
        every {
            auditLogRepository.saveAll(match(containsAllWithoutGeneratedFields(expectedLogs)))
        } returns expectedLogs

        disclosureLogService.saveDisclosureLogsForHanke(hanke, userId)

        verify {
            auditLogRepository.saveAll(match(containsAllWithoutGeneratedFields(expectedLogs)))
        }
    }

    @Test
    fun `saveDisclosureLogsForHanke saves identical audit logs only once`() {
        val yhteystieto = HankeYhteystietoFactory.createDifferentiated(1)
        val hanke =
            HankeFactory.create().mutate {
                it.omistajat = mutableListOf(yhteystieto)
                it.arvioijat = mutableListOf(yhteystieto)
                it.toteuttajat = mutableListOf(yhteystieto)
            }
        val expectedLogs =
            listOf(AuditLogEntryFactory.createReadEntry(objectBefore = yhteystieto.toJsonString()))
        every {
            auditLogRepository.saveAll(match(containsAllWithoutGeneratedFields(expectedLogs)))
        } returns expectedLogs

        disclosureLogService.saveDisclosureLogsForHanke(hanke, userId)

        verify {
            auditLogRepository.saveAll(match(containsAllWithoutGeneratedFields(expectedLogs)))
        }
    }

    @Test
    fun `saveDisclosureLogsForHankkeet without hankkeet does nothing`() {
        disclosureLogService.saveDisclosureLogsForHankkeet(listOf(), userId)

        verify { auditLogRepository wasNot Called }
    }

    @Test
    fun `saveDisclosureLogsForHankkeet with hankkeet without yhteystiedot does nothing`() {
        val hankkeet = listOf(HankeFactory.create(), HankeFactory.create())

        disclosureLogService.saveDisclosureLogsForHankkeet(hankkeet, userId)

        verify { auditLogRepository wasNot Called }
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
        every {
            auditLogRepository.saveAll(match(containsAllWithoutGeneratedFields(expectedLogs)))
        } returns expectedLogs

        disclosureLogService.saveDisclosureLogsForHankkeet(hankkeet, userId)

        verify {
            auditLogRepository.saveAll(match(containsAllWithoutGeneratedFields(expectedLogs)))
        }
    }

    @Test
    fun `saveDisclosureLogsForHankkeet saves identical audit logs only once`() {
        val hankkeet =
            listOf(
                HankeFactory.create().withYhteystiedot(),
                HankeFactory.create().withYhteystiedot()
            )
        val expectedLogs = AuditLogEntryFactory.createReadEntriesForHanke(hankkeet[0])
        every {
            auditLogRepository.saveAll(match(containsAllWithoutGeneratedFields(expectedLogs)))
        } returns expectedLogs

        disclosureLogService.saveDisclosureLogsForHankkeet(hankkeet, userId)

        verify {
            auditLogRepository.saveAll(match(containsAllWithoutGeneratedFields(expectedLogs)))
        }
    }

    @Test
    fun `saveDisclosureLogsForApplication with null application does nothing`() {
        disclosureLogService.saveDisclosureLogsForApplication(null, userId)

        verify { auditLogRepository wasNot Called }
    }

    @Test
    fun `saveDisclosureLogsForApplication with company customers and no contacts does nothing`() {
        val customerWithoutContacts =
            CustomerWithContacts(AlluDataFactory.createCompanyCustomer(name = "First"), listOf())
        val contractorWithoutContacts =
            CustomerWithContacts(AlluDataFactory.createCompanyCustomer(name = "Second"), listOf())
        val application =
            applicationDto(
                applicationData =
                    AlluDataFactory.createCableReportApplication(
                        customerWithContacts = customerWithoutContacts,
                        contractorWithContacts = contractorWithoutContacts
                    )
            )

        disclosureLogService.saveDisclosureLogsForApplication(application, userId)

        verify { auditLogRepository wasNot Called }
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
            applicationDto(
                applicationData =
                    AlluDataFactory.createCableReportApplication(
                        customerWithContacts = customerWithoutContacts,
                        contractorWithContacts = contractorWithoutContacts
                    )
            )

        disclosureLogService.saveDisclosureLogsForApplication(application, userId)

        verify { auditLogRepository wasNot Called }
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
            applicationDto(
                applicationData =
                    AlluDataFactory.createCableReportApplication(
                        customerWithContacts = customerWithoutContacts,
                        contractorWithContacts = contractorWithoutContacts
                    )
            )

        disclosureLogService.saveDisclosureLogsForApplication(application, userId)

        verify { auditLogRepository wasNot Called }
    }

    @Test
    fun `saveDisclosureLogsForApplication with identical person customers logs only once`() {
        val customerWithoutContacts =
            CustomerWithContacts(AlluDataFactory.createPersonCustomer(), listOf())
        val contractorWithoutContacts =
            CustomerWithContacts(AlluDataFactory.createPersonCustomer(), listOf())
        val application =
            applicationDto(
                AlluDataFactory.createCableReportApplication(
                    customerWithContacts = customerWithoutContacts,
                    contractorWithContacts = contractorWithoutContacts
                )
            )
        val expectedLogs =
            listOf(
                AuditLogEntryFactory.createReadEntryForCustomer(customerWithoutContacts.customer)
            )
        every {
            auditLogRepository.saveAll(match(containsAllWithoutGeneratedFields(expectedLogs)))
        } returns expectedLogs

        disclosureLogService.saveDisclosureLogsForApplication(application, userId)

        verify {
            auditLogRepository.saveAll(match(containsAllWithoutGeneratedFields(expectedLogs)))
        }
    }

    @Test
    fun `saveDisclosureLogsForApplication with contacts logs contacts`() {
        val cableReportApplication = AlluDataFactory.createCableReportApplication()
        val contact = cableReportApplication.customerWithContacts.contacts[0]
        val expectedLogs = listOf(AuditLogEntryFactory.createReadEntryForContact(contact))
        every {
            auditLogRepository.saveAll(match(containsAllWithoutGeneratedFields(expectedLogs)))
        } returns expectedLogs
        val application = applicationDto(applicationData = cableReportApplication)

        disclosureLogService.saveDisclosureLogsForApplication(application, userId)

        verify {
            auditLogRepository.saveAll(match(containsAllWithoutGeneratedFields(expectedLogs)))
        }
    }

    @Test
    fun `saveDisclosureLogsForApplications logs customers and contacts from all applications while ignoring duplicates`() {
        val contacts = (1..8).map { AlluDataFactory.createContact(name = "Contact $it") }
        val customers = (1..4).map { AlluDataFactory.createPersonCustomer(name = "Customer $it") }
        val expectedLogs =
            (contacts.map { AuditLogEntryFactory.createReadEntryForContact(it) } +
                customers.map { AuditLogEntryFactory.createReadEntryForCustomer(it) })
        assertEquals(12, expectedLogs.size)
        every {
            auditLogRepository.saveAll(match(containsAllWithoutGeneratedFields(expectedLogs)))
        } returns expectedLogs
        val customersWithContacts =
            (0..3).map { i ->
                CustomerWithContacts(
                    customers[i],
                    listOf(contacts[i], contacts[i + 4], contacts[0])
                )
            }
        val applications =
            listOf(
                    AlluDataFactory.createCableReportApplication(
                        customerWithContacts = customersWithContacts[0],
                        contractorWithContacts = customersWithContacts[1],
                    ),
                    AlluDataFactory.createCableReportApplication(
                        customerWithContacts = customersWithContacts[2],
                        contractorWithContacts = customersWithContacts[3],
                    )
                )
                .map { applicationDto(it) }

        disclosureLogService.saveDisclosureLogsForApplications(applications, userId)

        verify {
            auditLogRepository.saveAll(match(containsAllWithoutGeneratedFields(expectedLogs)))
        }
    }

    @Test
    fun `saveDisclosureLogsForApplications with company customers and no contacts does nothing`() {
        disclosureLogService.saveDisclosureLogsForApplications(listOf(), userId)

        verify { auditLogRepository wasNot Called }
    }

    private fun containsAllWithoutGeneratedFields(
        expectedLogs: List<AuditLogEntry>,
    ): (List<AuditLogEntry>) -> Boolean = { entries ->
        withoutGeneratedFields(entries) equalsIgnoreOrder withoutGeneratedFields(expectedLogs)
    }

    private infix fun <T> List<T>.equalsIgnoreOrder(other: List<T>) =
        this.size == other.size && this.toSet() == other.toSet()

    private fun withoutGeneratedFields(entries: List<AuditLogEntry>): List<AuditLogEntry> =
        entries.map { it.copy(eventTime = null, id = null) }

    private fun applicationDto(applicationData: CableReportApplication): ApplicationDto =
        ApplicationDto(
            id = 1,
            alluid = null,
            applicationType = ApplicationType.CABLE_REPORT,
            applicationData = OBJECT_MAPPER.valueToTree(applicationData)
        )
}
