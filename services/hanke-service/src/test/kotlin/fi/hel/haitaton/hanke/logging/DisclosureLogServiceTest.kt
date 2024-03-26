package fi.hel.haitaton.hanke.logging

import assertk.assertThat
import assertk.assertions.containsAtLeast
import assertk.assertions.containsExactlyInAnyOrder
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.application.ApplicationContactType.HAKIJA
import fi.hel.haitaton.hanke.application.ApplicationContactType.TYON_SUORITTAJA
import fi.hel.haitaton.hanke.application.Contact
import fi.hel.haitaton.hanke.application.Customer
import fi.hel.haitaton.hanke.application.CustomerWithContacts
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.TEPPO
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.TEPPO_EMAIL
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.TEPPO_PHONE
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.TESTIHENKILO
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.withApplicationData
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.withContacts
import fi.hel.haitaton.hanke.factory.AuditLogEntryFactory
import fi.hel.haitaton.hanke.factory.HakemusResponseFactory
import fi.hel.haitaton.hanke.factory.HakemusResponseFactory.withContacts
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withOmistaja
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withRakennuttaja
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withToteuttaja
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withYhteystiedot
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import fi.hel.haitaton.hanke.factory.HankeYhteystietoFactory
import fi.hel.haitaton.hanke.gdpr.CollectionNode
import fi.hel.haitaton.hanke.gdpr.StringNode
import fi.hel.haitaton.hanke.hakemus.ContactResponse
import fi.hel.haitaton.hanke.hakemus.CustomerResponse
import fi.hel.haitaton.hanke.hakemus.CustomerWithContactsResponse
import fi.hel.haitaton.hanke.hasSameElementsAs
import fi.hel.haitaton.hanke.reformatJson
import fi.hel.haitaton.hanke.toJsonString
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class DisclosureLogServiceTest {

    private val userId = "test"
    private val hankeTunnus = "HAI-1234"

    private val auditLogService: AuditLogService = mockk(relaxed = true)
    private val disclosureLogService = DisclosureLogService(auditLogService)

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun cleanUp() {
        checkUnnecessaryStub()
        confirmVerified(auditLogService)
    }

    @Test
    fun `saveDisclosureLogsForProfiili saves audit log of the entire info`() {
        val info =
            CollectionNode(
                "user",
                listOf(
                    StringNode("id", "4f15afe1-51dc-4015-bb66-3a536295abea"),
                    StringNode("etunimi", TEPPO),
                    StringNode("sukunimi", TESTIHENKILO),
                    StringNode("sahkoposti", TEPPO_EMAIL),
                    StringNode("puhelin", TEPPO_PHONE),
                )
            )

        disclosureLogService.saveDisclosureLogsForProfiili(userId, info)

        val expectedObject =
            """
            {"key":"user","children":
              [
                {"key":"id","value":"4f15afe1-51dc-4015-bb66-3a536295abea"},
                {"key":"etunimi","value":"$TEPPO"},
                {"key":"sukunimi","value":"$TESTIHENKILO"},
                {"key":"sahkoposti","value":"$TEPPO_EMAIL"},
                {"key":"puhelin","value":"$TEPPO_PHONE"}
              ]
            }"""
                .reformatJson()
        val expectedEntry =
            AuditLogEntryFactory.createReadEntry(
                userId = PROFIILI_AUDIT_LOG_USERID,
                userRole = UserRole.SERVICE,
                objectType = ObjectType.GDPR_RESPONSE,
                objectId = userId,
                objectBefore = expectedObject
            )
        verify { auditLogService.create(expectedEntry) }
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
            HankeFactory.create().apply {
                omistajat = mutableListOf(yhteystieto)
                rakennuttajat = mutableListOf(yhteystieto)
                toteuttajat = mutableListOf(yhteystieto)
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
                HankeFactory.create().withOmistaja(5).withRakennuttaja(6).withToteuttaja(7)
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
    fun `saveDisclosureLogsForHankeKayttaja saves audit logs`() {
        val hankeKayttaja = HankeKayttajaFactory.createDto()
        val expectedLogs = AuditLogEntryFactory.createReadEntryForHankeKayttaja(hankeKayttaja)

        disclosureLogService.saveDisclosureLogsForHankeKayttaja(hankeKayttaja, userId)

        verify { auditLogService.createAll(listOf(expectedLogs)) }
    }

    @Test
    fun `saveDisclosureLogsForHankeKayttajat saves audit logs`() {
        val hankeKayttajat = HankeKayttajaFactory.createHankeKayttajat(amount = 2)
        val expectedLogs = AuditLogEntryFactory.createReadEntryForHankeKayttajat(hankeKayttajat)

        disclosureLogService.saveDisclosureLogsForHankeKayttajat(hankeKayttajat, userId)

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
            ApplicationFactory.createCompanyCustomer(name = "First").withContacts()
        val contractorWithoutContacts =
            ApplicationFactory.createCompanyCustomer(name = "Second").withContacts()
        val application =
            ApplicationFactory.createApplication(
                applicationData =
                    ApplicationFactory.createCableReportApplicationData(
                        customerWithContacts = customerWithoutContacts,
                        contractorWithContacts = contractorWithoutContacts
                    ),
                hankeTunnus = hankeTunnus,
            )

        disclosureLogService.saveDisclosureLogsForApplication(application, userId)

        verify { auditLogService wasNot Called }
    }

    @Test
    fun `saveDisclosureLogsForApplication doesn't save entries for blank contacts`() {
        val customerWithoutContacts =
            ApplicationFactory.createCompanyCustomer(name = "First").withContacts()
        val contractorWithoutContacts =
            ApplicationFactory.createCompanyCustomer(name = "Second")
                .withContacts(Contact("", "", "", ""))
        val application =
            ApplicationFactory.createApplication(
                applicationData =
                    ApplicationFactory.createCableReportApplicationData(
                        customerWithContacts = customerWithoutContacts,
                        contractorWithContacts = contractorWithoutContacts
                    ),
                hankeTunnus = hankeTunnus,
            )

        disclosureLogService.saveDisclosureLogsForApplication(application, userId)

        verify { auditLogService wasNot Called }
    }

    @Test
    fun `saveDisclosureLogsForApplication doesn't save entries for blank customers`() {
        val blankCustomer = Customer(type = CustomerType.PERSON, "", null, null, null)
        val blankCustomerWithCountry = Customer(type = CustomerType.PERSON, "", null, null, null)
        val customerWithoutContacts = CustomerWithContacts(blankCustomer, listOf())
        val contractorWithoutContacts = CustomerWithContacts(blankCustomerWithCountry, listOf())
        val application =
            ApplicationFactory.createApplication(
                applicationData =
                    ApplicationFactory.createCableReportApplicationData(
                        customerWithContacts = customerWithoutContacts,
                        contractorWithContacts = contractorWithoutContacts
                    ),
                hankeTunnus = hankeTunnus,
            )

        disclosureLogService.saveDisclosureLogsForApplication(application, userId)

        verify { auditLogService wasNot Called }
    }

    @Test
    fun `saveDisclosureLogsForApplication with identical person customers logs every instance`() {
        val customerWithoutContacts =
            CustomerWithContacts(ApplicationFactory.createPersonCustomer(), listOf())
        val application =
            ApplicationFactory.createApplication(
                applicationData =
                    ApplicationFactory.createCableReportApplicationData(
                        customerWithContacts = customerWithoutContacts,
                        contractorWithContacts = customerWithoutContacts,
                    ),
                hankeTunnus = hankeTunnus,
            )
        val capturedLogs = slot<Collection<AuditLogEntry>>()
        every { auditLogService.createAll(capture(capturedLogs)) } returns mutableListOf()

        disclosureLogService.saveDisclosureLogsForApplication(application, userId)

        val expectedLogs =
            listOf(HAKIJA, TYON_SUORITTAJA).map {
                AuditLogEntryFactory.createReadEntryForCustomer(
                    application.id!!,
                    customerWithoutContacts.customer,
                    it
                )
            }
        assertThat(capturedLogs.captured).hasSameElementsAs(expectedLogs)
        verify(exactly = 1) { auditLogService.createAll(any()) }
    }

    @Test
    fun `saveDisclosureLogsForApplication with contacts logs contacts`() {
        val applicationId = 41L
        val cableReportApplication = ApplicationFactory.createCableReportApplicationData()
        val firstContact = cableReportApplication.customerWithContacts!!.contacts[0]
        val secondContact = cableReportApplication.contractorWithContacts!!.contacts[0]
        val application =
            ApplicationFactory.createApplication(
                id = applicationId,
                applicationData = cableReportApplication,
                hankeTunnus = hankeTunnus
            )
        val capturedLogs = slot<Collection<AuditLogEntry>>()
        every { auditLogService.createAll(capture(capturedLogs)) } returns mutableListOf()

        disclosureLogService.saveDisclosureLogsForApplication(application, userId)

        assertThat(capturedLogs.captured)
            .containsAtLeast(
                AuditLogEntryFactory.createReadEntryForContact(applicationId, firstContact, HAKIJA),
                AuditLogEntryFactory.createReadEntryForContact(
                    applicationId,
                    secondContact,
                    TYON_SUORITTAJA
                ),
            )
        verify(exactly = 1) { auditLogService.createAll(any()) }
    }

    @ParameterizedTest(name = "{displayName}({arguments})")
    @EnumSource(Status::class)
    fun `saveDisclosureLogsForAllu saves logs with the given status`(expectedStatus: Status) {
        val applicationId = 41L
        val cableReportApplication = ApplicationFactory.createCableReportApplicationData()
        val firstContact = cableReportApplication.customerWithContacts!!.contacts[0]
        val secondContact = cableReportApplication.contractorWithContacts!!.contacts[0]
        val expectedLogs =
            listOf(HAKIJA to firstContact, TYON_SUORITTAJA to secondContact).map { (role, contact)
                ->
                AuditLogEntryFactory.createReadEntryForContact(applicationId, contact, role)
                    .copy(
                        objectType = ObjectType.ALLU_CONTACT,
                        status = expectedStatus,
                        userId = ALLU_AUDIT_LOG_USERID,
                        userRole = UserRole.SERVICE,
                    )
            }
        val capturedLogs = slot<Collection<AuditLogEntry>>()
        every { auditLogService.createAll(capture(capturedLogs)) } returns mutableListOf()

        disclosureLogService.saveDisclosureLogsForAllu(
            applicationId,
            cableReportApplication,
            expectedStatus
        )

        assertThat(capturedLogs.captured).hasSameElementsAs(expectedLogs)
        verify(exactly = 1) { auditLogService.createAll(any()) }
    }

    @Nested
    inner class SaveDisclosureLogsForHakemusResponse {
        @Test
        fun `with company customers and no contacts does nothing`() {
            val customerWithoutContacts =
                HakemusResponseFactory.companyCustomer(name = "First").withContacts()
            ApplicationFactory.createCompanyCustomer(name = "First").withContacts()
            val contractorWithoutContacts =
                HakemusResponseFactory.companyCustomer(name = "Second").withContacts()
            val hakemusResponse =
                HakemusResponseFactory.create(
                    applicationData =
                        HakemusResponseFactory.createJohtoselvitysHakemusDataResponse(
                            customerWithContacts = customerWithoutContacts,
                            contractorWithContacts = contractorWithoutContacts
                        ),
                    hankeTunnus = hankeTunnus,
                )

            disclosureLogService.saveDisclosureLogsForHakemusResponse(hakemusResponse, userId)

            verify { auditLogService wasNot Called }
        }

        @Test
        fun `doesn't save entries for blank contacts`() {
            val customerWithoutContacts =
                HakemusResponseFactory.companyCustomer(name = "First").withContacts()
            val contractorWithoutContacts =
                HakemusResponseFactory.companyCustomer(name = "Second")
                    .withContacts(ContactResponse(UUID.randomUUID(), "", "", "", ""))
            val hakemusResponse =
                HakemusResponseFactory.create(
                    applicationData =
                        HakemusResponseFactory.createJohtoselvitysHakemusDataResponse(
                            customerWithContacts = customerWithoutContacts,
                            contractorWithContacts = contractorWithoutContacts
                        ),
                    hankeTunnus = hankeTunnus,
                )

            disclosureLogService.saveDisclosureLogsForHakemusResponse(hakemusResponse, userId)

            verify { auditLogService wasNot Called }
        }

        @Test
        fun `doesn't save entries for blank customers`() {
            val blankCustomer =
                CustomerResponse(UUID.randomUUID(), CustomerType.PERSON, "", "", "", "")
            val customerWithoutContacts = CustomerWithContactsResponse(blankCustomer, listOf())
            val contractorWithoutContacts = CustomerWithContactsResponse(blankCustomer, listOf())
            val hakemusResponse =
                HakemusResponseFactory.create(
                    applicationData =
                        HakemusResponseFactory.createJohtoselvitysHakemusDataResponse(
                            customerWithContacts = customerWithoutContacts,
                            contractorWithContacts = contractorWithoutContacts
                        ),
                    hankeTunnus = hankeTunnus,
                )

            disclosureLogService.saveDisclosureLogsForHakemusResponse(hakemusResponse, userId)

            verify { auditLogService wasNot Called }
        }

        @Test
        fun `with identical person customers logs every instance`() {
            val customerWithoutContacts =
                CustomerWithContactsResponse(HakemusResponseFactory.personCustomer(), listOf())
            val hakemusResponse =
                HakemusResponseFactory.create(
                    applicationData =
                        HakemusResponseFactory.createJohtoselvitysHakemusDataResponse(
                            customerWithContacts = customerWithoutContacts,
                            contractorWithContacts = customerWithoutContacts,
                        ),
                    hankeTunnus = hankeTunnus,
                )
            val capturedLogs = slot<Collection<AuditLogEntry>>()
            every { auditLogService.createAll(capture(capturedLogs)) } returns mutableListOf()

            disclosureLogService.saveDisclosureLogsForHakemusResponse(hakemusResponse, userId)

            val expectedLogs =
                listOf(HAKIJA, TYON_SUORITTAJA).map {
                    AuditLogEntryFactory.createReadEntryForCustomerResponse(
                        hakemusResponse.id,
                        customerWithoutContacts.customer,
                        it
                    )
                }
            assertThat(capturedLogs.captured).hasSameElementsAs(expectedLogs)
            verify(exactly = 1) { auditLogService.createAll(any()) }
        }

        @Test
        fun `with contacts logs contacts`() {
            val applicationId = 41L
            val hakemusDataResponse =
                HakemusResponseFactory.createJohtoselvitysHakemusDataResponse()
            val firstContact = hakemusDataResponse.customerWithContacts!!.contacts[0]
            val secondContact = hakemusDataResponse.contractorWithContacts!!.contacts[0]
            val application =
                HakemusResponseFactory.create(
                    applicationId = applicationId,
                    applicationData = hakemusDataResponse,
                    hankeTunnus = hankeTunnus
                )
            val capturedLogs = slot<Collection<AuditLogEntry>>()
            every { auditLogService.createAll(capture(capturedLogs)) } returns mutableListOf()

            disclosureLogService.saveDisclosureLogsForHakemusResponse(application, userId)

            assertThat(capturedLogs.captured)
                .containsAtLeast(
                    AuditLogEntryFactory.createReadEntryForContactResponse(
                        applicationId,
                        firstContact,
                        HAKIJA
                    ),
                    AuditLogEntryFactory.createReadEntryForContactResponse(
                        applicationId,
                        secondContact,
                        TYON_SUORITTAJA
                    ),
                )
            verify(exactly = 1) { auditLogService.createAll(any()) }
        }
    }

    @Nested
    inner class SaveDisclosureLogsForAllu {
        @ParameterizedTest(name = "{displayName}({arguments})")
        @EnumSource(Status::class)
        fun `saves logs with the given status`(expectedStatus: Status) {
            val applicationId = 41L
            val cableReportApplication = ApplicationFactory.createCableReportApplicationData()
            val firstContact = cableReportApplication.customerWithContacts!!.contacts[0]
            val secondContact = cableReportApplication.contractorWithContacts!!.contacts[0]
            val expectedLogs =
                listOf(HAKIJA to firstContact, TYON_SUORITTAJA to secondContact).map {
                    (role, contact) ->
                    AuditLogEntryFactory.createReadEntryForContact(applicationId, contact, role)
                        .copy(
                            objectType = ObjectType.ALLU_CONTACT,
                            status = expectedStatus,
                            userId = ALLU_AUDIT_LOG_USERID,
                            userRole = UserRole.SERVICE,
                        )
                }
            val capturedLogs = slot<Collection<AuditLogEntry>>()
            every { auditLogService.createAll(capture(capturedLogs)) } returns mutableListOf()

            disclosureLogService.saveDisclosureLogsForAllu(
                applicationId,
                cableReportApplication,
                expectedStatus
            )

            assertThat(capturedLogs.captured).hasSameElementsAs(expectedLogs)
            verify(exactly = 1) { auditLogService.createAll(any()) }
        }
    }

    @Test
    fun `saveDisclosureLogsForApplications logs customers and contacts from all applications while ignoring duplicates`() {
        val contacts =
            (1..8).map { ApplicationFactory.createContact(firstName = "Contact", lastName = "$it") }
        val customers =
            (1..4).map { ApplicationFactory.createPersonCustomer(name = "Customer $it") }
        val customersWithContacts =
            customers.withIndex().map { (i, customer) ->
                CustomerWithContacts(
                    customer,
                    listOf(contacts[2 * i], contacts[2 * i + 1], contacts[0])
                )
            }
        val applications =
            listOf(
                ApplicationFactory.createApplication(id = 1, hankeTunnus = hankeTunnus)
                    .withApplicationData(
                        customerWithContacts = customersWithContacts[0],
                        contractorWithContacts = customersWithContacts[1],
                    ),
                ApplicationFactory.createApplication(id = 2, hankeTunnus = hankeTunnus)
                    .withApplicationData(
                        customerWithContacts = customersWithContacts[2],
                        contractorWithContacts = customersWithContacts[3],
                    )
            )
        val capturedLogs = slot<Collection<AuditLogEntry>>()
        every { auditLogService.createAll(capture(capturedLogs)) } returns mutableListOf()

        disclosureLogService.saveDisclosureLogsForApplications(applications, userId)

        assertThat(capturedLogs.captured)
            .containsExactlyInAnyOrder(
                AuditLogEntryFactory.createReadEntryForCustomer(1, customers[0], HAKIJA),
                // The first contact is added twice for the first customer, but identical entries
                // are logged only once
                AuditLogEntryFactory.createReadEntryForContact(1, contacts[0], HAKIJA),
                AuditLogEntryFactory.createReadEntryForContact(1, contacts[1], HAKIJA),
                AuditLogEntryFactory.createReadEntryForCustomer(1, customers[1], TYON_SUORITTAJA),
                AuditLogEntryFactory.createReadEntryForContact(1, contacts[0], TYON_SUORITTAJA),
                AuditLogEntryFactory.createReadEntryForContact(1, contacts[2], TYON_SUORITTAJA),
                AuditLogEntryFactory.createReadEntryForContact(1, contacts[3], TYON_SUORITTAJA),
                AuditLogEntryFactory.createReadEntryForCustomer(2, customers[2], HAKIJA),
                AuditLogEntryFactory.createReadEntryForContact(2, contacts[0], HAKIJA),
                AuditLogEntryFactory.createReadEntryForContact(2, contacts[4], HAKIJA),
                AuditLogEntryFactory.createReadEntryForContact(2, contacts[5], HAKIJA),
                AuditLogEntryFactory.createReadEntryForCustomer(2, customers[3], TYON_SUORITTAJA),
                AuditLogEntryFactory.createReadEntryForContact(2, contacts[0], TYON_SUORITTAJA),
                AuditLogEntryFactory.createReadEntryForContact(2, contacts[6], TYON_SUORITTAJA),
                AuditLogEntryFactory.createReadEntryForContact(2, contacts[7], TYON_SUORITTAJA),
            )
        verify(exactly = 1) { auditLogService.createAll(any()) }
    }

    @Test
    fun `saveDisclosureLogsForApplications with company customers and no contacts does nothing`() {
        disclosureLogService.saveDisclosureLogsForApplications(listOf(), userId)

        verify { auditLogService wasNot Called }
    }

    @Nested
    inner class SaveDisclosureLogsForDecision {
        @Test
        fun `saves log with application details but decision type`() {
            val applicationId = 42L
            val alluId = 2
            val alluStatus = ApplicationStatus.DECISION
            val applicationIdentifier = "JS2300050-2"
            val application =
                ApplicationFactory.createApplication(
                    id = applicationId,
                    alluid = alluId,
                    alluStatus = alluStatus,
                    applicationIdentifier = applicationIdentifier,
                    hankeTunnus = hankeTunnus
                )
            val expectedObject =
                """{
                  "id": $applicationId,
                  "alluid": $alluId,
                  "alluStatus": "$alluStatus",
                  "applicationIdentifier": "$applicationIdentifier",
                  "applicationType": "CABLE_REPORT",
                  "hankeTunnus": "$hankeTunnus"
                }"""
                    .reformatJson()
            val expectedLog =
                AuditLogEntryFactory.createReadEntry(
                    userId,
                    objectType = ObjectType.CABLE_REPORT,
                    objectId = application.id!!,
                    objectBefore = expectedObject
                )

            disclosureLogService.saveDisclosureLogsForCableReport(application.toMetadata(), userId)

            verify { auditLogService.create(expectedLog) }
        }
    }

    private fun containsAll(
        expectedLogs: List<AuditLogEntry>,
    ): (List<AuditLogEntry>) -> Boolean = { entries -> entries equalsIgnoreOrder expectedLogs }

    private infix fun <T> List<T>.equalsIgnoreOrder(other: List<T>): Boolean =
        this.size == other.size && this.toSet() == other.toSet()
}
