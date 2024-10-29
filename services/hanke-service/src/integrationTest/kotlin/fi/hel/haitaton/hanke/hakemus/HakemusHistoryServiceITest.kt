package fi.hel.haitaton.hanke.hakemus

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.doesNotContain
import assertk.assertions.each
import assertk.assertions.extracting
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.prop
import assertk.assertions.single
import assertk.assertions.startsWith
import com.icegreen.greenmail.configuration.GreenMailConfiguration
import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.ServerSetupTest
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.allu.AlluStatusRepository
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.InformationRequestFieldKey
import fi.hel.haitaton.hanke.asUtc
import fi.hel.haitaton.hanke.attachment.PDF_BYTES
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.attachment.common.TestFile
import fi.hel.haitaton.hanke.factory.AlluFactory
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import fi.hel.haitaton.hanke.factory.TaydennysFactory
import fi.hel.haitaton.hanke.firstReceivedMessage
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.taydennys.TaydennysRepository
import fi.hel.haitaton.hanke.taydennys.TaydennyspyyntoEntity
import fi.hel.haitaton.hanke.taydennys.TaydennyspyyntoRepository
import fi.hel.haitaton.hanke.test.USERNAME
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verify
import io.mockk.verifySequence
import jakarta.mail.internet.MimeMessage
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension

@ExtendWith(OutputCaptureExtension::class)
class HakemusHistoryServiceITest(
    @Autowired private val historyService: HakemusHistoryService,
    @Autowired private val hakemusRepository: HakemusRepository,
    @Autowired private val alluStatusRepository: AlluStatusRepository,
    @Autowired private val taydennyspyyntoRepository: TaydennyspyyntoRepository,
    @Autowired private val taydennysRepository: TaydennysRepository,
    @Autowired private val hakemusFactory: HakemusFactory,
    @Autowired private val taydennysFactory: TaydennysFactory,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val hankeKayttajaFactory: HankeKayttajaFactory,
    @Autowired private val alluClient: AlluClient,
    @Autowired private val fileClient: MockFileClient,
) : IntegrationTest() {
    companion object {
        @JvmField
        @RegisterExtension
        val greenMail: GreenMailExtension =
            GreenMailExtension(ServerSetupTest.SMTP)
                .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication())
    }

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(alluClient)
    }

    @Nested
    inner class HandleApplicationUpdates {

        /** The timestamp used in the initial DB migration. */
        private val placeholderUpdateTime = OffsetDateTime.parse("2017-01-01T00:00:00Z")
        private val updateTime = OffsetDateTime.parse("2022-10-09T06:36:51Z")
        private val alluId = 42
        private val identifier = ApplicationHistoryFactory.DEFAULT_APPLICATION_IDENTIFIER

        @Test
        fun `updates the last updated time with empty histories`() {
            assertThat(hakemusRepository.findAll()).isEmpty()
            assertThat(alluStatusRepository.getLastUpdateTime().asUtc())
                .isEqualTo(placeholderUpdateTime)

            historyService.handleHakemusUpdates(listOf(), updateTime)

            assertThat(alluStatusRepository.getLastUpdateTime().asUtc()).isEqualTo(updateTime)
        }

        @Test
        fun `updates the hakemus statuses in the correct order`() {
            hakemusFactory.builder(USERNAME).withStatus(alluId = alluId).save()
            val firstEventTime = ZonedDateTime.parse("2022-09-05T14:15:16Z")
            val history =
                ApplicationHistoryFactory.create(
                    alluId,
                    ApplicationHistoryFactory.createEvent(
                        firstEventTime.plusDays(5), ApplicationStatus.PENDING),
                    ApplicationHistoryFactory.createEvent(
                        firstEventTime.plusDays(10), ApplicationStatus.HANDLING),
                    ApplicationHistoryFactory.createEvent(
                        firstEventTime, ApplicationStatus.PENDING),
                )

            historyService.handleHakemusUpdates(listOf(history), updateTime)

            assertThat(alluStatusRepository.getLastUpdateTime().asUtc()).isEqualTo(updateTime)
            val application = hakemusRepository.getOneByAlluid(alluId)
            assertThat(application)
                .isNotNull()
                .prop("alluStatus", HakemusEntity::alluStatus)
                .isEqualTo(ApplicationStatus.HANDLING)
            assertThat(application!!.applicationIdentifier).isEqualTo(identifier)
        }

        @Test
        fun `doesn't update status or identifier when the update status is REPLACED`() {
            val originalTunnus = "JS2400001-12"
            hakemusFactory
                .builder(USERNAME)
                .withStatus(
                    alluId = alluId,
                    status = ApplicationStatus.DECISION,
                    identifier = originalTunnus,
                )
                .save()
            val history =
                ApplicationHistoryFactory.create(
                    alluId,
                    ApplicationHistoryFactory.createEvent(
                        applicationIdentifier = "JS2400001-13",
                        newStatus = ApplicationStatus.REPLACED,
                    ),
                )

            historyService.handleHakemusUpdates(listOf(history), updateTime)

            assertThat(hakemusRepository.findAll()).single().all {
                prop(HakemusEntity::alluStatus).isEqualTo(ApplicationStatus.DECISION)
                prop(HakemusEntity::applicationIdentifier).isEqualTo(originalTunnus)
            }
        }

        @Test
        fun `ignores missing hakemus`() {
            assertThat(hakemusRepository.findAll()).isEmpty()
            assertThat(alluStatusRepository.getLastUpdateTime().asUtc())
                .isEqualTo(placeholderUpdateTime)
            val hanke = hankeFactory.saveMinimal()
            hakemusFactory.builder(USERNAME, hanke).withStatus(alluId = alluId).save()
            hakemusFactory.builder(USERNAME, hanke).withStatus(alluId = alluId + 2).save()
            val histories =
                listOf(
                    ApplicationHistoryFactory.create(alluId, "JS2300082"),
                    ApplicationHistoryFactory.create(alluId + 1, "JS2300083"),
                    ApplicationHistoryFactory.create(alluId + 2, "JS2300084"),
                )

            historyService.handleHakemusUpdates(histories, updateTime)

            assertThat(alluStatusRepository.getLastUpdateTime().asUtc()).isEqualTo(updateTime)
            val applications = hakemusRepository.findAll()
            assertThat(applications).hasSize(2)
            assertThat(applications.map { it.alluid }).containsExactlyInAnyOrder(alluId, alluId + 2)
            assertThat(applications.map { it.alluStatus })
                .containsExactlyInAnyOrder(
                    ApplicationStatus.PENDING_CLIENT, ApplicationStatus.PENDING_CLIENT)
            assertThat(applications.map { it.applicationIdentifier })
                .containsExactlyInAnyOrder("JS2300082", "JS2300084")
        }

        @Test
        fun `sends email to the contacts when a johtoselvityshakemus gets a decision`() {
            val hanke = hankeFactory.saveMinimal()
            val hakija = hankeKayttajaFactory.saveUser(hankeId = hanke.id)
            hakemusFactory
                .builder(USERNAME, hanke)
                .withStatus(ApplicationStatus.HANDLING, alluId, identifier)
                .hakija(hakija)
                .saveEntity()
            val histories =
                listOf(
                    ApplicationHistoryFactory.create(
                        alluId,
                        ApplicationHistoryFactory.createEvent(
                            applicationIdentifier = identifier,
                            newStatus = ApplicationStatus.DECISION)),
                )

            historyService.handleHakemusUpdates(histories, updateTime)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.allRecipients).hasSize(1)
            assertThat(email.allRecipients[0].toString()).isEqualTo(hakija.sahkoposti)
            assertThat(email.subject)
                .isEqualTo(
                    "Haitaton: Johtoselvitys $identifier / Ledningsutredning $identifier / Cable report $identifier")
        }

        @ParameterizedTest
        @EnumSource(
            ApplicationStatus::class, names = ["DECISION", "OPERATIONAL_CONDITION", "FINISHED"])
        fun `sends email to the contacts when a kaivuilmoitus gets a decision`(
            applicationStatus: ApplicationStatus
        ) {
            val identifier = "KP2300001"
            val hanke = hankeFactory.saveMinimal()
            val hakija = hankeKayttajaFactory.saveUser(hankeId = hanke.id)
            hakemusFactory
                .builder(USERNAME, hanke, ApplicationType.EXCAVATION_NOTIFICATION)
                .withStatus(ApplicationStatus.HANDLING, alluId, identifier)
                .hakija(hakija)
                .saveEntity()
            val histories =
                listOf(
                    ApplicationHistoryFactory.create(
                        alluId,
                        ApplicationHistoryFactory.createEvent(
                            applicationIdentifier = identifier,
                            newStatus = applicationStatus,
                        ),
                    ),
                )
            mockAlluDownload(applicationStatus)
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse(id = alluId, applicationId = identifier)

            historyService.handleHakemusUpdates(histories, updateTime)

            val email = greenMail.firstReceivedMessage()
            assertThat(email.allRecipients).hasSize(1)
            assertThat(email.allRecipients[0].toString()).isEqualTo(hakija.sahkoposti)
            assertThat(email.subject)
                .isEqualTo(
                    "Haitaton: Kaivuilmoitukseen KP2300001 liittyvä päätös on ladattavissa / Kaivuilmoitukseen KP2300001 liittyvä päätös on ladattavissa / Kaivuilmoitukseen KP2300001 liittyvä päätös on ladattavissa")
            verifyAlluDownload(applicationStatus)
            verify { alluClient.getApplicationInformation(alluId) }
        }

        @ParameterizedTest
        @EnumSource(
            ApplicationStatus::class, names = ["DECISION", "OPERATIONAL_CONDITION", "FINISHED"])
        fun `downloads the document when a kaivuilmoitus gets a decision`(
            status: ApplicationStatus
        ) {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withStatus(ApplicationStatus.HANDLING, alluId, identifier)
                    .save()
            val histories =
                listOf(
                    ApplicationHistoryFactory.create(
                        alluId,
                        ApplicationHistoryFactory.createEvent(
                            applicationIdentifier = identifier,
                            newStatus = status,
                        )),
                )
            mockAlluDownload(status)
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse()

            historyService.handleHakemusUpdates(histories, updateTime)

            assertThat(fileClient.listBlobs(Container.PAATOKSET))
                .single()
                .prop(TestFile::path)
                .startsWith("${hakemus.id}/")
            verifyAlluDownload(status)
            verify { alluClient.getApplicationInformation(alluId) }
        }

        @Test
        fun `sends one email for every user with edit applications permission when the new status is WAITING_INFORMATION`() {
            val hanke = hankeFactory.saveMinimal()
            val hakija =
                hankeKayttajaFactory.saveIdentifiedUser(
                    hankeId = hanke.id,
                    sahkoposti = "hakija@yritys.test",
                    kayttooikeustaso = Kayttooikeustaso.KAIKKI_OIKEUDET,
                )
            val suorittaja =
                hankeKayttajaFactory.saveIdentifiedUser(
                    hankeId = hanke.id,
                    sahkoposti = "suorittaja@yritys.test",
                    kayttooikeustaso = Kayttooikeustaso.HAKEMUSASIOINTI,
                )
            val rakennuttaja =
                hankeKayttajaFactory.saveIdentifiedUser(
                    hankeId = hanke.id,
                    sahkoposti = "rakennuttaja@yritys.test",
                    kayttooikeustaso = Kayttooikeustaso.HANKEMUOKKAUS,
                )
            val asianhoitaja =
                hankeKayttajaFactory.saveIdentifiedUser(
                    hankeId = hanke.id,
                    sahkoposti = "asianhoitaja@yritys.test",
                    kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
                )
            hakemusFactory
                .builder(hanke)
                .withStatus(ApplicationStatus.HANDLING, alluId, identifier)
                .hakija(hakija)
                .tyonSuorittaja(suorittaja, hakija)
                .rakennuttaja(rakennuttaja, suorittaja, asianhoitaja)
                .asianhoitaja(asianhoitaja, rakennuttaja, suorittaja, hakija)
                .save()
            val event =
                ApplicationHistoryFactory.createEvent(
                    applicationIdentifier = identifier,
                    newStatus = ApplicationStatus.WAITING_INFORMATION,
                )
            val histories = listOf(ApplicationHistoryFactory.create(alluId, event))
            every { alluClient.getInformationRequest(alluId) } returns
                AlluFactory.createInformationRequest()

            historyService.handleHakemusUpdates(histories, updateTime)

            val emails = greenMail.receivedMessages
            val recipients = emails.map { it.allRecipients.toList() }.flatten()
            assertThat(recipients)
                .extracting { it.toString() }
                .containsExactlyInAnyOrder(hakija.sahkoposti, suorittaja.sahkoposti)
            assertThat(emails).each {
                it.prop(MimeMessage::getSubject)
                    .isEqualTo(
                        "Haitaton: Hakemuksellesi on tullut täydennyspyyntö / Hakemuksellesi on tullut täydennyspyyntö / Hakemuksellesi on tullut täydennyspyyntö")
            }
            verifySequence { alluClient.getInformationRequest(alluId) }
        }

        @Test
        fun `gets the information request from Allu and saves it when the new status is WAITING_INFORMATION`() {
            val hanke = hankeFactory.saveMinimal()
            val hakemus =
                hakemusFactory
                    .builder(hanke)
                    .withStatus(ApplicationStatus.HANDLING, alluId, identifier)
                    .save()
            val event =
                ApplicationHistoryFactory.createEvent(
                    applicationIdentifier = identifier,
                    newStatus = ApplicationStatus.WAITING_INFORMATION,
                )
            val histories = listOf(ApplicationHistoryFactory.create(alluId, event))
            every { alluClient.getInformationRequest(alluId) } returns
                AlluFactory.createInformationRequest(applicationAlluId = alluId)

            historyService.handleHakemusUpdates(histories, updateTime)

            assertThat(taydennyspyyntoRepository.findAll()).single().all {
                prop(TaydennyspyyntoEntity::alluId)
                    .isEqualTo(AlluFactory.DEFAULT_INFORMATION_REQUEST_ID)
                prop(TaydennyspyyntoEntity::applicationId).isEqualTo(hakemus.id)
                prop(TaydennyspyyntoEntity::kentat)
                    .containsOnly(
                        InformationRequestFieldKey.OTHER to
                            AlluFactory.DEFAULT_INFORMATION_REQUEST_DESCRIPTION)
            }
            verifySequence { alluClient.getInformationRequest(alluId) }
        }

        @Test
        fun `removes any taydennyspyynto and taydennys when the new status is HANDLING`(
            output: CapturedOutput
        ) {
            val hakemus =
                hakemusFactory
                    .builder()
                    .withStatus(ApplicationStatus.WAITING_INFORMATION, 42)
                    .save()
            taydennysFactory.save(applicationId = hakemus.id)
            val event =
                ApplicationHistoryFactory.createEvent(
                    applicationIdentifier = identifier,
                    newStatus = ApplicationStatus.HANDLING,
                )
            val histories = listOf(ApplicationHistoryFactory.create(alluId, event))

            historyService.handleHakemusUpdates(histories, updateTime)

            assertThat(taydennyspyyntoRepository.findAll()).isEmpty()
            assertThat(taydennysRepository.findAll()).isEmpty()
            val updatedHakemus = hakemusRepository.getOneByAlluid(alluId)
            assertThat(updatedHakemus)
                .isNotNull()
                .prop(HakemusEntity::alluStatus)
                .isEqualTo(ApplicationStatus.HANDLING)
            assertThat(output).doesNotContain("ERROR")
        }

        @Test
        fun `updates the status when the new status is HANDLING and there is no taydennyspyynto`(
            output: CapturedOutput
        ) {
            hakemusFactory.builder().withStatus(ApplicationStatus.WAITING_INFORMATION, 42).save()
            val event =
                ApplicationHistoryFactory.createEvent(
                    applicationIdentifier = identifier,
                    newStatus = ApplicationStatus.HANDLING,
                )
            val histories = listOf(ApplicationHistoryFactory.create(alluId, event))

            historyService.handleHakemusUpdates(histories, updateTime)

            val updatedHakemus = hakemusRepository.getOneByAlluid(alluId)
            assertThat(updatedHakemus)
                .isNotNull()
                .prop(HakemusEntity::alluStatus)
                .isEqualTo(ApplicationStatus.HANDLING)
            assertThat(output).doesNotContain("ERROR")
        }

        @Test
        fun `logs an error when the new status is HANDLING, the previous status is not WAITING_INFORMATION and the hakemus has a taydennyspyynto`(
            output: CapturedOutput
        ) {
            val hakemus = hakemusFactory.builder().withStatus(ApplicationStatus.DECISION, 42).save()
            taydennysFactory.save(applicationId = hakemus.id)
            val event =
                ApplicationHistoryFactory.createEvent(
                    applicationIdentifier = identifier,
                    newStatus = ApplicationStatus.HANDLING,
                )
            val histories = listOf(ApplicationHistoryFactory.create(alluId, event))

            historyService.handleHakemusUpdates(histories, updateTime)

            assertThat(taydennyspyyntoRepository.findAll()).isEmpty()
            assertThat(taydennysRepository.findAll()).isEmpty()
            val updatedHakemus = hakemusRepository.getOneByAlluid(alluId)
            assertThat(updatedHakemus)
                .isNotNull()
                .prop(HakemusEntity::alluStatus)
                .isEqualTo(ApplicationStatus.HANDLING)
            assertThat(output).contains("ERROR")
            assertThat(output)
                .contains(
                    "A hakemus moved to handling and it had a täydennyspyyntö, but the previous state was not 'HANDLING'. status=DECISION")
        }

        private fun mockAlluDownload(status: ApplicationStatus) =
            every { getPdfMethod(status)(alluId) } returns PDF_BYTES

        private fun verifyAlluDownload(status: ApplicationStatus) = verify {
            getPdfMethod(status)(alluId)
        }

        private fun getPdfMethod(applicationStatus: ApplicationStatus) =
            when (applicationStatus) {
                ApplicationStatus.DECISION -> alluClient::getDecisionPdf
                ApplicationStatus.OPERATIONAL_CONDITION -> alluClient::getOperationalConditionPdf
                ApplicationStatus.FINISHED -> alluClient::getWorkFinishedPdf
                else -> throw IllegalArgumentException()
            }
    }
}
