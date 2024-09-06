package fi.hel.haitaton.hanke.logging

import assertk.all
import assertk.assertThat
import assertk.assertions.containsAtLeast
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.prop
import assertk.assertions.single
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.TEPPO
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.TEPPO_EMAIL
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.TEPPO_PHONE
import fi.hel.haitaton.hanke.factory.ApplicationFactory.Companion.TESTIHENKILO
import fi.hel.haitaton.hanke.factory.AuditLogEntryFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HakemusResponseFactory
import fi.hel.haitaton.hanke.factory.HakemusResponseFactory.withContacts
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory.withYhteyshenkilo
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withOmistaja
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withRakennuttaja
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withToteuttaja
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withYhteystiedot
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import fi.hel.haitaton.hanke.factory.HankeYhteystietoFactory
import fi.hel.haitaton.hanke.gdpr.CollectionNode
import fi.hel.haitaton.hanke.gdpr.StringNode
import fi.hel.haitaton.hanke.hakemus.ApplicationContactType.HAKIJA
import fi.hel.haitaton.hanke.hakemus.ApplicationContactType.TYON_SUORITTAJA
import fi.hel.haitaton.hanke.hakemus.ContactResponse
import fi.hel.haitaton.hanke.hakemus.CustomerResponse
import fi.hel.haitaton.hanke.hakemus.CustomerWithContactsResponse
import fi.hel.haitaton.hanke.hakemus.HakemusDataMapper.toAlluCableReportData
import fi.hel.haitaton.hanke.hakemus.HakemusDataMapper.toAlluContact
import fi.hel.haitaton.hanke.hakemus.HakemusDataMapper.toAlluCustomer
import fi.hel.haitaton.hanke.hakemus.HakemusDataMapper.toAlluExcavationNotificationData
import fi.hel.haitaton.hanke.hakemus.toLaskutusyhteystieto
import fi.hel.haitaton.hanke.hasSameElementsAs
import fi.hel.haitaton.hanke.paatos.PaatosMetadata
import fi.hel.haitaton.hanke.paatos.PaatosTila
import fi.hel.haitaton.hanke.paatos.PaatosTyyppi
import fi.hel.haitaton.hanke.reformatJson
import fi.hel.haitaton.hanke.test.AuditLogEntryAsserts.hasAlluActor
import fi.hel.haitaton.hanke.test.AuditLogEntryAsserts.isSuccess
import fi.hel.haitaton.hanke.toJsonString
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifySequence
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
                ))

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
                objectBefore = expectedObject,
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
                HankeFactory.create().withOmistaja(5).withRakennuttaja(6).withToteuttaja(7),
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
                HankeFactory.create().withYhteystiedot(),
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

    @Nested
    inner class SaveDisclosureLogsForHakemusResponse {
        @Test
        fun `with company customers and no contacts does nothing`() {
            val customerWithoutContacts =
                HakemusResponseFactory.companyCustomer(name = "First").withContacts()
            val contractorWithoutContacts =
                HakemusResponseFactory.companyCustomer(name = "Second").withContacts()
            val hakemusResponse =
                HakemusResponseFactory.create(
                    applicationData =
                        HakemusResponseFactory.createJohtoselvitysHakemusDataResponse(
                            customerWithContacts = customerWithoutContacts,
                            contractorWithContacts = contractorWithoutContacts,
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
                            contractorWithContacts = contractorWithoutContacts,
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
                            contractorWithContacts = contractorWithoutContacts,
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
                        it,
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
                    hankeTunnus = hankeTunnus,
                )
            val capturedLogs = slot<Collection<AuditLogEntry>>()
            every { auditLogService.createAll(capture(capturedLogs)) } returns mutableListOf()

            disclosureLogService.saveDisclosureLogsForHakemusResponse(application, userId)

            assertThat(capturedLogs.captured)
                .containsAtLeast(
                    AuditLogEntryFactory.createReadEntryForContactResponse(
                        applicationId, firstContact, HAKIJA),
                    AuditLogEntryFactory.createReadEntryForContactResponse(
                        applicationId, secondContact, TYON_SUORITTAJA),
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
            val hakija = HakemusyhteystietoFactory.create().withYhteyshenkilo()
            val rakennuttaja = HakemusyhteystietoFactory.create().withYhteyshenkilo()
            val hakemusData =
                HakemusFactory.createJohtoselvityshakemusData(
                    customerWithContacts = hakija,
                    contractorWithContacts = rakennuttaja,
                )
            val expectedLogs =
                listOf(
                        HAKIJA to hakija.yhteyshenkilot[0],
                        TYON_SUORITTAJA to rakennuttaja.yhteyshenkilot[0])
                    .map { (role, contact) ->
                        AuditLogEntryFactory.createReadEntryForContact(
                                applicationId, contact.toAlluContact(), role)
                            .copy(
                                status = expectedStatus,
                                userId = ALLU_AUDIT_LOG_USERID,
                                userRole = UserRole.SERVICE,
                            )
                    }
            val capturedLogs = slot<Collection<AuditLogEntry>>()
            every { auditLogService.createAll(capture(capturedLogs)) } returns mutableListOf()

            disclosureLogService.saveDisclosureLogsForAllu(
                applicationId,
                hakemusData.toAlluCableReportData(hankeTunnus),
                expectedStatus,
            )

            assertThat(capturedLogs.captured).hasSameElementsAs(expectedLogs)
            verify(exactly = 1) { auditLogService.createAll(any()) }
        }

        @Test
        fun `saves logs for person customers`() {
            val applicationId = 41L
            val personCustomer = HakemusyhteystietoFactory.createPerson()
            val companyCustomer = HakemusyhteystietoFactory.create()
            val hakemusData =
                HakemusFactory.createJohtoselvityshakemusData(
                    customerWithContacts = personCustomer,
                    contractorWithContacts = companyCustomer,
                )
            val capturedLogs = slot<Collection<AuditLogEntry>>()
            every { auditLogService.createAll(capture(capturedLogs)) } returns mutableListOf()

            disclosureLogService.saveDisclosureLogsForAllu(
                applicationId,
                hakemusData.toAlluCableReportData(hankeTunnus),
                Status.SUCCESS,
            )

            assertThat(capturedLogs.captured).single().all {
                isSuccess()
                hasAlluActor()
                prop(AuditLogEntry::objectAfter).isNull()
                prop(AuditLogEntry::objectBefore)
                    .isEqualTo(
                        AlluCustomerWithRole(HAKIJA, personCustomer.toAlluCustomer().customer)
                            .toJsonString())
            }
            verify(exactly = 1) { auditLogService.createAll(any()) }
        }

        @Test
        fun `skips person customers when they have no personal data`() {
            val applicationId = 41L
            val personCustomer =
                HakemusyhteystietoFactory.createPerson(
                    nimi = "", puhelinnumero = "", sahkoposti = "", registryKey = "")
            val companyCustomer = HakemusyhteystietoFactory.create()
            val hakemusData =
                HakemusFactory.createJohtoselvityshakemusData(
                    customerWithContacts = personCustomer,
                    contractorWithContacts = companyCustomer,
                )

            disclosureLogService.saveDisclosureLogsForAllu(
                applicationId,
                hakemusData.toAlluCableReportData(hankeTunnus),
                Status.SUCCESS,
            )

            verify { auditLogService wasNot Called }
        }

        @Test
        fun `saves logs with invoicing customer when invoicing customer is a person`() {
            val applicationId = 41L
            val invoicingCustomer = ApplicationFactory.createPersonInvoicingCustomer()
            val hakemusData =
                HakemusFactory.createKaivuilmoitusData(
                    customerWithContacts = HakemusyhteystietoFactory.create().withYhteyshenkilo(),
                    contractorWithContacts = HakemusyhteystietoFactory.create().withYhteyshenkilo(),
                    invoicingCustomer = invoicingCustomer.toLaskutusyhteystieto(null),
                )
            val expectedLog =
                AuditLogEntryFactory.createReadEntry(
                    objectId = applicationId,
                    objectType = ObjectType.ALLU_CUSTOMER,
                    objectBefore =
                        AlluMetaCustomerWithRole(
                                MetaCustomerType.INVOICING, invoicingCustomer.toAlluData(""))
                            .toJsonString(),
                    userId = ALLU_AUDIT_LOG_USERID,
                    userRole = UserRole.SERVICE,
                )
            val capturedLogs = slot<Collection<AuditLogEntry>>()
            every { auditLogService.createAll(capture(capturedLogs)) } returns mutableListOf()

            disclosureLogService.saveDisclosureLogsForAllu(
                applicationId,
                hakemusData.toAlluExcavationNotificationData(hankeTunnus),
                Status.SUCCESS,
            )

            val customerLogs =
                capturedLogs.captured.filter { it.objectType == ObjectType.ALLU_CUSTOMER }
            assertThat(customerLogs).single().isEqualTo(expectedLog)
            verifySequence { auditLogService.createAll(any()) }
        }

        @Test
        fun `saves logs without invoicing customer when invoicing customer is a company`() {
            val applicationId = 41L
            val invoicingCustomer = ApplicationFactory.createCompanyInvoicingCustomer()
            val hakemusData =
                HakemusFactory.createKaivuilmoitusData(
                    customerWithContacts = HakemusyhteystietoFactory.create().withYhteyshenkilo(),
                    contractorWithContacts = HakemusyhteystietoFactory.create().withYhteyshenkilo(),
                    invoicingCustomer = invoicingCustomer.toLaskutusyhteystieto(null),
                )
            val capturedLogs = slot<Collection<AuditLogEntry>>()
            every { auditLogService.createAll(capture(capturedLogs)) } returns mutableListOf()

            disclosureLogService.saveDisclosureLogsForAllu(
                applicationId,
                hakemusData.toAlluExcavationNotificationData(hankeTunnus),
                Status.SUCCESS,
            )

            val customerLogs =
                capturedLogs.captured.filter { it.objectType == ObjectType.ALLU_CUSTOMER }
            assertThat(customerLogs).isEmpty()
            verifySequence { auditLogService.createAll(any()) }
        }
    }

    @Nested
    inner class SaveDisclosureLogsForCableReport {
        @Test
        fun `saves log with application details but cable report type`() {
            val applicationId = 42L
            val alluId = 2
            val alluStatus = ApplicationStatus.DECISION
            val applicationIdentifier = "JS2300050-2"
            val hakemus =
                HakemusFactory.create(
                    id = applicationId,
                    alluid = alluId,
                    alluStatus = alluStatus,
                    applicationIdentifier = applicationIdentifier,
                    hankeTunnus = hankeTunnus,
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
                    objectId = hakemus.id,
                    objectBefore = expectedObject,
                )

            disclosureLogService.saveDisclosureLogsForCableReport(hakemus.toMetadata(), userId)

            verify { auditLogService.create(expectedLog) }
        }
    }

    @Nested
    inner class SaveDisclosureLogsForPaatos {
        @Test
        fun `saves log with paatos type`() {
            val paatosId = UUID.fromString("fa0f538a-09ef-48bf-ba6a-681e0c979b14")
            val hakemusId = 42L
            val hakemustunnus = "JS2300050-2"
            val tyyppi = PaatosTyyppi.TOIMINNALLINEN_KUNTO
            val tila = PaatosTila.KORVATTU
            val expectedObject =
                """{
                  "id": "$paatosId",
                  "hakemusId": $hakemusId,
                  "hakemustunnus": "$hakemustunnus",
                  "tyyppi": "$tyyppi",
                  "tila": "$tila"
                }"""
                    .reformatJson()
            val expectedLog =
                AuditLogEntryFactory.createReadEntry(
                    userId,
                    objectType = ObjectType.PAATOS,
                    objectId = paatosId,
                    objectBefore = expectedObject)
            val metadata = PaatosMetadata(paatosId, hakemusId, hakemustunnus, tyyppi, tila)

            disclosureLogService.saveDisclosureLogsForPaatos(metadata, userId)

            verifySequence { auditLogService.create(expectedLog) }
        }
    }

    private fun containsAll(
        expectedLogs: List<AuditLogEntry>,
    ): (List<AuditLogEntry>) -> Boolean = { entries -> entries equalsIgnoreOrder expectedLogs }

    private infix fun <T> List<T>.equalsIgnoreOrder(other: List<T>): Boolean =
        this.size == other.size && this.toSet() == other.toSet()
}
