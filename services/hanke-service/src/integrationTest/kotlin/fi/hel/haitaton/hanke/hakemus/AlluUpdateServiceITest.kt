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
import fi.hel.haitaton.hanke.allu.AlluEventErrorEntity
import fi.hel.haitaton.hanke.allu.AlluEventErrorRepository
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
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory.DEFAULT_APPLICATION_IDENTIFIER
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory.asList
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory.withDefaultEvents
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory.withEvent
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory.withEvents
import fi.hel.haitaton.hanke.factory.DateFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import fi.hel.haitaton.hanke.factory.MuutosilmoitusFactory
import fi.hel.haitaton.hanke.factory.TaydennysFactory
import fi.hel.haitaton.hanke.firstReceivedMessage
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusRepository
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.taydennys.TaydennysRepository
import fi.hel.haitaton.hanke.taydennys.TaydennyspyyntoEntity
import fi.hel.haitaton.hanke.taydennys.TaydennyspyyntoRepository
import fi.hel.haitaton.hanke.test.Asserts.isRecent
import fi.hel.haitaton.hanke.test.USERNAME
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
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
class AlluUpdateServiceITest(
    @Autowired private val updateService: AlluUpdateService,
    @Autowired private val hakemusHistoryService: HakemusHistoryService,
    @Autowired private val hakemusRepository: HakemusRepository,
    @Autowired private val alluStatusRepository: AlluStatusRepository,
    @Autowired private val alluEventErrorRepository: AlluEventErrorRepository,
    @Autowired private val muutosilmoitusRepository: MuutosilmoitusRepository,
    @Autowired private val taydennyspyyntoRepository: TaydennyspyyntoRepository,
    @Autowired private val taydennysRepository: TaydennysRepository,
    @Autowired private val hakemusFactory: HakemusFactory,
    @Autowired private val taydennysFactory: TaydennysFactory,
    @Autowired private val hankeFactory: HankeFactory,
    @Autowired private val hankeKayttajaFactory: HankeKayttajaFactory,
    @Autowired private val muutosilmoitusFactory: MuutosilmoitusFactory,
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

    private val alluId = 42
    private val identifier = DEFAULT_APPLICATION_IDENTIFIER
    /** The timestamp used in the initial DB migration. */
    private val placeholderUpdateTime = OffsetDateTime.parse("2017-01-01T00:00:00Z")

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
    inner class HandleNewUpdates {

        @Test
        fun `updates the last updated time with empty histories`() {
            assertThat(alluEventErrorRepository.findAll()).isEmpty()
            assertThat(hakemusRepository.findAll()).isEmpty()
            assertThat(alluStatusRepository.getLastUpdateTime().asUtc())
                .isEqualTo(placeholderUpdateTime)

            updateService.handleUpdates()

            assertThat(alluStatusRepository.getLastUpdateTime().asUtc()).isRecent()
        }

        @Test
        fun `updates the hakemus statuses in the correct order`() {
            hakemusFactory.builder(USERNAME).withStatus(alluId = alluId).save()
            val firstEventTime = ZonedDateTime.parse("2022-09-05T14:15:16Z")
            val history =
                ApplicationHistoryFactory.create(alluId)
                    .withEvent(firstEventTime.plusDays(5), ApplicationStatus.PENDING)
                    .withEvent(firstEventTime.plusDays(10), ApplicationStatus.HANDLING)
                    .withEvent(firstEventTime, ApplicationStatus.PENDING)
                    .asList()
            every {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
            } returns history

            updateService.handleUpdates()

            assertThat(alluStatusRepository.getLastUpdateTime().asUtc()).isRecent()
            val application = hakemusRepository.getOneByAlluid(alluId)
            assertThat(application)
                .isNotNull()
                .prop("alluStatus", HakemusEntity::alluStatus)
                .isEqualTo(ApplicationStatus.HANDLING)
            assertThat(application!!.applicationIdentifier).isEqualTo(identifier)
            assertThat(alluEventErrorRepository.findAll()).isEmpty()
            verifySequence {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
            }
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
                ApplicationHistoryFactory.create(alluId)
                    .withEvent(
                        applicationIdentifier = "JS2400001-13",
                        newStatus = ApplicationStatus.REPLACED,
                    )
                    .asList()
            every {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
            } returns history

            updateService.handleUpdates()

            assertThat(hakemusRepository.findAll()).single().all {
                prop(HakemusEntity::alluStatus).isEqualTo(ApplicationStatus.DECISION)
                prop(HakemusEntity::applicationIdentifier).isEqualTo(originalTunnus)
            }
            assertThat(alluEventErrorRepository.findAll()).isEmpty()
            verifySequence {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
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
            val history =
                listOf(
                    ApplicationHistoryFactory.create(alluId).withDefaultEvents("JS2300082"),
                    ApplicationHistoryFactory.create(alluId + 1).withDefaultEvents("JS2300083"),
                    ApplicationHistoryFactory.create(alluId + 2).withDefaultEvents("JS2300084"),
                )
            every {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId, alluId + 2),
                    placeholderUpdateTime.toZonedDateTime(),
                )
            } returns history

            updateService.handleUpdates()

            assertThat(alluStatusRepository.getLastUpdateTime().asUtc()).isRecent()
            val applications = hakemusRepository.findAll()
            assertThat(applications).hasSize(2)
            assertThat(applications.map { it.alluid }).containsExactlyInAnyOrder(alluId, alluId + 2)
            assertThat(applications.map { it.alluStatus })
                .containsExactlyInAnyOrder(
                    ApplicationStatus.PENDING_CLIENT,
                    ApplicationStatus.PENDING_CLIENT,
                )
            assertThat(applications.map { it.applicationIdentifier })
                .containsExactlyInAnyOrder("JS2300082", "JS2300084")
            assertThat(alluEventErrorRepository.findAll()).isEmpty()
            verifySequence {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId, alluId + 2),
                    placeholderUpdateTime.toZonedDateTime(),
                )
            }
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
            val history =
                ApplicationHistoryFactory.create(alluId)
                    .withEvent(
                        applicationIdentifier = identifier,
                        newStatus = ApplicationStatus.DECISION,
                    )
                    .asList()
            every {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
            } returns history

            updateService.handleUpdates()

            val email = greenMail.firstReceivedMessage()
            assertThat(email.allRecipients).hasSize(1)
            assertThat(email.allRecipients[0].toString()).isEqualTo(hakija.sahkoposti)
            assertThat(email.subject)
                .isEqualTo(
                    "Haitaton: Johtoselvitys $identifier / Ledningsutredning $identifier / Cable report $identifier"
                )
            verifySequence {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
            }
        }

        @ParameterizedTest
        @EnumSource(
            ApplicationStatus::class,
            names = ["DECISION", "OPERATIONAL_CONDITION", "FINISHED"],
        )
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
            val history =
                ApplicationHistoryFactory.create(alluId)
                    .withEvent(applicationIdentifier = identifier, newStatus = applicationStatus)
                    .asList()
            every {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
            } returns history
            every { getPdfMethod(applicationStatus)(alluId) } returns PDF_BYTES
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse(id = alluId, applicationId = identifier)

            updateService.handleUpdates()

            val email = greenMail.firstReceivedMessage()
            assertThat(email.allRecipients).hasSize(1)
            assertThat(email.allRecipients[0].toString()).isEqualTo(hakija.sahkoposti)
            assertThat(email.subject)
                .isEqualTo(
                    "Haitaton: Kaivuilmoitukseen KP2300001 liittyvä päätös on ladattavissa / Beslut om grävningsanmälan KP2300001 kan laddas ner / The decision concerning an excavation notification KP2300001 can be downloaded"
                )
            verifySequence {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
                getPdfMethod(applicationStatus)(alluId)
                alluClient.getApplicationInformation(alluId)
            }
        }

        @ParameterizedTest
        @EnumSource(
            ApplicationStatus::class,
            names = ["DECISION", "OPERATIONAL_CONDITION", "FINISHED"],
        )
        fun `downloads the document when a kaivuilmoitus gets a decision`(
            status: ApplicationStatus
        ) {
            val hakemus =
                hakemusFactory
                    .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                    .withStatus(ApplicationStatus.HANDLING, alluId, identifier)
                    .save()
            val history =
                ApplicationHistoryFactory.create(alluId)
                    .withEvent(applicationIdentifier = identifier, newStatus = status)
                    .asList()
            every {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
            } returns history
            every { getPdfMethod(status)(alluId) } returns PDF_BYTES
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse()

            updateService.handleUpdates()

            assertThat(fileClient.listBlobs(Container.PAATOKSET))
                .single()
                .prop(TestFile::path)
                .startsWith("${hakemus.id}/")
            verifySequence {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
                getPdfMethod(status)(alluId)
                alluClient.getApplicationInformation(alluId)
            }
        }

        @ParameterizedTest
        @EnumSource(ApplicationType::class)
        fun `merges changes from muutosilmoitus to the hakemus when a hakemus gets a decision`(
            applicationType: ApplicationType
        ) {
            val muutosilmoitus =
                muutosilmoitusFactory
                    .builder(applicationType, alluId = alluId)
                    .withEndTime(DateFactory.getEndDatetime().plusDays(1))
                    .withSent()
                    .save()
            val history =
                ApplicationHistoryFactory.create(alluId)
                    .withEvent(
                        applicationIdentifier = identifier,
                        newStatus = ApplicationStatus.DECISION,
                    )
                    .asList()
            every {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
            } returns history
            if (applicationType == ApplicationType.EXCAVATION_NOTIFICATION) {
                every { getPdfMethod(ApplicationStatus.DECISION)(alluId) } returns PDF_BYTES
                every { alluClient.getApplicationInformation(alluId) } returns
                    AlluFactory.createAlluApplicationResponse()
            }

            updateService.handleUpdates()

            val hakemus =
                hakemusRepository.findOneById(muutosilmoitus.hakemusId)!!.hakemusEntityData
            assertThat(hakemus.endTime).isEqualTo(muutosilmoitus.hakemusData.endTime)
            assertThat(muutosilmoitusRepository.findAll()).isEmpty()
            if (applicationType == ApplicationType.EXCAVATION_NOTIFICATION) {
                verifySequence {
                    alluClient.getApplicationStatusHistories(
                        listOf(alluId),
                        placeholderUpdateTime.toZonedDateTime(),
                    )
                    getPdfMethod(ApplicationStatus.DECISION)(alluId)
                    alluClient.getApplicationInformation(alluId)
                }
            } else {
                verifySequence {
                    alluClient.getApplicationStatusHistories(
                        listOf(alluId),
                        placeholderUpdateTime.toZonedDateTime(),
                    )
                }
            }
        }

        @ParameterizedTest
        @EnumSource(ApplicationType::class)
        fun `merges changes from muutosilmoitus to the hakemus when a hakemus gets a taydennyspyynto`(
            applicationType: ApplicationType
        ) {
            val newName = "Updated for muutosilmoitus"
            val muutosilmoitus =
                muutosilmoitusFactory
                    .builder(type = applicationType, alluId = alluId)
                    .withName(newName)
                    .withSent()
                    .save()
            val history =
                ApplicationHistoryFactory.create(alluId)
                    .withEvent(newStatus = ApplicationStatus.WAITING_INFORMATION)
                    .asList()
            every {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
            } returns history
            every { alluClient.getInformationRequest(alluId) } returns
                AlluFactory.createInformationRequest(applicationAlluId = alluId)

            updateService.handleUpdates()

            val hakemus =
                hakemusRepository.findOneById(muutosilmoitus.hakemusId)!!.hakemusEntityData
            assertThat(hakemus.name).isEqualTo(newName)
            assertThat(muutosilmoitusRepository.findAll()).isEmpty()
            verifySequence {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
                alluClient.getInformationRequest(alluId)
            }
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
            val history =
                ApplicationHistoryFactory.create(alluId)
                    .withEvent(
                        applicationIdentifier = identifier,
                        newStatus = ApplicationStatus.WAITING_INFORMATION,
                    )
                    .asList()
            every {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
            } returns history
            every { alluClient.getInformationRequest(alluId) } returns
                AlluFactory.createInformationRequest()

            updateService.handleUpdates()

            val emails = greenMail.receivedMessages
            val recipients = emails.map { it.allRecipients.toList() }.flatten()
            assertThat(recipients)
                .extracting { it.toString() }
                .containsExactlyInAnyOrder(hakija.sahkoposti, suorittaja.sahkoposti)
            assertThat(emails).each {
                it.prop(MimeMessage::getSubject)
                    .isEqualTo(
                        "Haitaton: Hakemuksellesi on tullut täydennyspyyntö / Din ansökan har fått en begäran om komplettering / There is a request for supplementary information for your application"
                    )
            }
            verifySequence {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
                alluClient.getInformationRequest(alluId)
            }
        }

        @Test
        fun `gets the information request from Allu and saves it when the new status is WAITING_INFORMATION`() {
            val hanke = hankeFactory.saveMinimal()
            val hakemus =
                hakemusFactory
                    .builder(hanke)
                    .withStatus(ApplicationStatus.HANDLING, alluId, identifier)
                    .save()
            val history =
                ApplicationHistoryFactory.create(alluId)
                    .withEvent(
                        applicationIdentifier = identifier,
                        newStatus = ApplicationStatus.WAITING_INFORMATION,
                    )
                    .asList()
            every {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
            } returns history
            every { alluClient.getInformationRequest(alluId) } returns
                AlluFactory.createInformationRequest(applicationAlluId = alluId)

            updateService.handleUpdates()

            assertThat(taydennyspyyntoRepository.findAll()).single().all {
                prop(TaydennyspyyntoEntity::alluId)
                    .isEqualTo(AlluFactory.DEFAULT_INFORMATION_REQUEST_ID)
                prop(TaydennyspyyntoEntity::applicationId).isEqualTo(hakemus.id)
                prop(TaydennyspyyntoEntity::kentat)
                    .containsOnly(
                        InformationRequestFieldKey.OTHER to
                            AlluFactory.DEFAULT_INFORMATION_REQUEST_DESCRIPTION
                    )
            }
            verifySequence {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
                alluClient.getInformationRequest(alluId)
            }
        }

        @Test
        fun `doesn't send email or save the taydennyspyynto when the new status is WAITING_INFORMATION but Allu doesn't have the taydennyspyynto`() {
            hakemusFactory
                .builder()
                .withStatus(ApplicationStatus.HANDLING, alluId, identifier)
                .save()
            val history =
                ApplicationHistoryFactory.create(alluId)
                    .withEvent(
                        applicationIdentifier = identifier,
                        newStatus = ApplicationStatus.WAITING_INFORMATION,
                    )
                    .asList()
            every {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
            } returns history
            every { alluClient.getInformationRequest(alluId) } returns null

            updateService.handleUpdates()

            val emails = greenMail.receivedMessages
            assertThat(emails).isEmpty()
            assertThat(taydennyspyyntoRepository.findAll()).isEmpty()
            verifySequence {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
                alluClient.getInformationRequest(alluId)
            }
        }

        @Test
        fun `removes any taydennyspyynto and taydennys when the new status is HANDLING`(
            output: CapturedOutput
        ) {
            val hakemus =
                hakemusFactory
                    .builder()
                    .withStatus(ApplicationStatus.WAITING_INFORMATION, alluId)
                    .save()
            taydennysFactory.save(applicationId = hakemus.id)
            val history =
                ApplicationHistoryFactory.create(alluId)
                    .withEvent(
                        applicationIdentifier = identifier,
                        newStatus = ApplicationStatus.HANDLING,
                    )
                    .asList()
            every {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
            } returns history

            updateService.handleUpdates()

            assertThat(taydennyspyyntoRepository.findAll()).isEmpty()
            assertThat(taydennysRepository.findAll()).isEmpty()
            val updatedHakemus = hakemusRepository.getOneByAlluid(alluId)
            assertThat(updatedHakemus)
                .isNotNull()
                .prop(HakemusEntity::alluStatus)
                .isEqualTo(ApplicationStatus.HANDLING)
            assertThat(output).doesNotContain("ERROR")
            verifySequence {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
            }
        }

        @Test
        fun `sends emails when removing the taydennyspyynto`() {
            val hakemus =
                hakemusFactory
                    .builder()
                    .withStatus(ApplicationStatus.WAITING_INFORMATION, alluId, identifier)
                    .hakija(Kayttooikeustaso.KAIKKI_OIKEUDET)
                    .tyonSuorittaja(Kayttooikeustaso.HAKEMUSASIOINTI)
                    .rakennuttaja(Kayttooikeustaso.HANKEMUOKKAUS)
                    .asianhoitaja(Kayttooikeustaso.KATSELUOIKEUS)
                    .save()
            taydennysFactory.save(applicationId = hakemus.id)
            val history =
                ApplicationHistoryFactory.create(alluId)
                    .withEvent(
                        applicationIdentifier = identifier,
                        newStatus = ApplicationStatus.HANDLING,
                    )
                    .asList()
            every {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
            } returns history

            updateService.handleUpdates()

            val emails = greenMail.receivedMessages
            val recipients = emails.map { it.allRecipients.toList() }.flatten()
            assertThat(recipients)
                .extracting { it.toString() }
                .containsExactlyInAnyOrder(
                    HankeKayttajaFactory.KAYTTAJA_INPUT_HAKIJA.email,
                    HankeKayttajaFactory.KAYTTAJA_INPUT_SUORITTAJA.email,
                )
            assertThat(emails).each {
                it.prop(MimeMessage::getSubject)
                    .isEqualTo(
                        "Haitaton: Hakemustasi koskeva täydennyspyyntö on peruttu / Begäran om komplettering som gäller din ansökan har återtagits / The request for supplementary information concerning your application has been cancelled"
                    )
            }
            verifySequence {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
            }
        }

        @Test
        fun `updates the status when the new status is HANDLING and there is no taydennyspyynto`(
            output: CapturedOutput
        ) {
            hakemusFactory.builder().withStatus(ApplicationStatus.WAITING_INFORMATION, 42).save()
            val history =
                ApplicationHistoryFactory.create(alluId)
                    .withEvent(
                        applicationIdentifier = identifier,
                        newStatus = ApplicationStatus.HANDLING,
                    )
                    .asList()
            every {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
            } returns history

            updateService.handleUpdates()

            val updatedHakemus = hakemusRepository.getOneByAlluid(alluId)
            assertThat(updatedHakemus)
                .isNotNull()
                .prop(HakemusEntity::alluStatus)
                .isEqualTo(ApplicationStatus.HANDLING)
            assertThat(output).doesNotContain("ERROR")
            verifySequence {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
            }
        }

        @Test
        fun `logs an error when the new status is HANDLING, the previous status is not WAITING_INFORMATION and the hakemus has a taydennyspyynto`(
            output: CapturedOutput
        ) {
            val hakemus = hakemusFactory.builder().withStatus(ApplicationStatus.DECISION, 42).save()
            taydennysFactory.save(applicationId = hakemus.id)
            val history =
                ApplicationHistoryFactory.create(alluId)
                    .withEvent(
                        applicationIdentifier = identifier,
                        newStatus = ApplicationStatus.HANDLING,
                    )
                    .asList()
            every {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
            } returns history

            updateService.handleUpdates()

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
                    "A hakemus moved to handling and it had a täydennyspyyntö, " +
                        "but the previous state was not 'WAITING_INFORMATION'. status=DECISION"
                )
            verifySequence {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
            }
        }

        @Test
        fun `handles updates for other applications when one fails`() {
            assertThat(hakemusRepository.findAll()).isEmpty()
            assertThat(alluStatusRepository.getLastUpdateTime().asUtc())
                .isEqualTo(placeholderUpdateTime)
            val hanke = hankeFactory.saveMinimal()
            hakemusFactory.builder(USERNAME, hanke).withStatus(alluId = alluId).save()
            hakemusFactory
                .builder(USERNAME, hanke)
                .withStatus(status = ApplicationStatus.HANDLING, alluId = alluId + 1)
                .save()
            hakemusFactory.builder(USERNAME, hanke).withStatus(alluId = alluId + 2).save()
            val history =
                listOf(
                    ApplicationHistoryFactory.create(alluId).withDefaultEvents("JS2300082"),
                    ApplicationHistoryFactory.create(alluId + 1)
                        .withEvent(
                            applicationIdentifier = "JS2300083",
                            newStatus = ApplicationStatus.WAITING_INFORMATION,
                            eventTime = ZonedDateTime.parse("2022-09-05T14:15:16Z"),
                        ),
                    ApplicationHistoryFactory.create(alluId + 2).withDefaultEvents("JS2300084"),
                )
            val exception = RuntimeException("Test exception")
            every {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId, alluId + 1, alluId + 2),
                    placeholderUpdateTime.toZonedDateTime(),
                )
            } returns history
            every { alluClient.getInformationRequest(alluId + 1) } throws exception

            updateService.handleUpdates()

            assertThat(alluStatusRepository.getLastUpdateTime().asUtc()).isRecent()
            val applications = hakemusRepository.findAll()
            assertThat(applications.map { it.alluStatus })
                .containsExactlyInAnyOrder(
                    ApplicationStatus.PENDING_CLIENT,
                    ApplicationStatus.HANDLING,
                    ApplicationStatus.PENDING_CLIENT,
                )
            assertThat(alluEventErrorRepository.findAll()).single().all {
                prop(AlluEventErrorEntity::alluId).isEqualTo(alluId + 1)
                prop(AlluEventErrorEntity::newStatus)
                    .isEqualTo(ApplicationStatus.WAITING_INFORMATION)
                prop(AlluEventErrorEntity::applicationIdentifier).isEqualTo("JS2300083")
                prop(AlluEventErrorEntity::eventTime)
                    .isEqualTo(ZonedDateTime.parse("2022-09-05T14:15:16Z"))
                prop(AlluEventErrorEntity::stackTrace).isEqualTo(exception.stackTraceToString())
            }

            verifySequence {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId, alluId + 1, alluId + 2),
                    placeholderUpdateTime.toZonedDateTime(),
                )
                alluClient.getInformationRequest(alluId + 1)
            }
        }

        @Test
        fun `does not handle following updates for an application when one fails`() {
            assertThat(hakemusRepository.findAll()).isEmpty()
            assertThat(alluStatusRepository.getLastUpdateTime().asUtc())
                .isEqualTo(placeholderUpdateTime)
            assertThat(alluEventErrorRepository.findAll()).isEmpty()
            val hanke = hankeFactory.saveMinimal()
            hakemusFactory.builder(USERNAME, hanke).withStatus(alluId = alluId).save()
            val history =
                ApplicationHistoryFactory.create(alluId)
                    .withEvents(
                        ApplicationHistoryFactory.createEvent(
                            newStatus = ApplicationStatus.PENDING,
                            eventTime = ZonedDateTime.parse("2022-09-05T14:15:16Z"),
                        ),
                        ApplicationHistoryFactory.createEvent(
                            newStatus = ApplicationStatus.HANDLING,
                            eventTime = ZonedDateTime.parse("2022-09-05T14:16:16Z"),
                        ),
                        ApplicationHistoryFactory.createEvent(
                            newStatus = ApplicationStatus.WAITING_INFORMATION,
                            eventTime = ZonedDateTime.parse("2022-09-05T14:17:16Z"),
                        ),
                        ApplicationHistoryFactory.createEvent(
                            newStatus = ApplicationStatus.HANDLING,
                            eventTime = ZonedDateTime.parse("2022-09-05T14:18:16Z"),
                        ),
                        ApplicationHistoryFactory.createEvent(
                            newStatus = ApplicationStatus.PENDING_CLIENT,
                            eventTime = ZonedDateTime.parse("2022-09-05T14:19:16Z"),
                        ),
                    )
                    .asList()
            val exception = RuntimeException("Test exception")
            every {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
            } returns history
            every { alluClient.getInformationRequest(alluId) } throws exception

            updateService.handleUpdates()

            assertThat(alluStatusRepository.getLastUpdateTime().asUtc()).isRecent()
            val application = hakemusRepository.findAll().single()
            assertThat(application.alluStatus).isEqualTo(ApplicationStatus.HANDLING)
            assertThat(alluEventErrorRepository.findAll()).single().all {
                prop(AlluEventErrorEntity::alluId).isEqualTo(alluId)
                prop(AlluEventErrorEntity::newStatus)
                    .isEqualTo(ApplicationStatus.WAITING_INFORMATION)
                prop(AlluEventErrorEntity::applicationIdentifier)
                    .isEqualTo(DEFAULT_APPLICATION_IDENTIFIER)
                prop(AlluEventErrorEntity::eventTime)
                    .isEqualTo(ZonedDateTime.parse("2022-09-05T14:17:16Z"))
                prop(AlluEventErrorEntity::stackTrace).isEqualTo(exception.stackTraceToString())
            }

            verifySequence {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
                alluClient.getInformationRequest(alluId)
            }
        }

        private fun getPdfMethod(applicationStatus: ApplicationStatus) =
            when (applicationStatus) {
                ApplicationStatus.DECISION -> alluClient::getDecisionPdf
                ApplicationStatus.OPERATIONAL_CONDITION -> alluClient::getOperationalConditionPdf
                ApplicationStatus.FINISHED -> alluClient::getWorkFinishedPdf
                else -> throw IllegalArgumentException()
            }
    }

    @Nested
    inner class HandleFailedUpdates {

        @Test
        fun `successfully handle previously failed history`(output: CapturedOutput) {
            val hanke = hankeFactory.saveMinimal()
            hakemusFactory
                .builder(USERNAME, hanke)
                .withStatus(status = ApplicationStatus.HANDLING, alluId = alluId)
                .save()
            val eventTime = ZonedDateTime.parse("2022-09-05T14:17:16Z")
            hakemusHistoryService.saveErrors(
                listOf(
                    ApplicationHistoryFactory.createError(
                        alluId = alluId,
                        eventTime = eventTime,
                        newStatus = ApplicationStatus.WAITING_INFORMATION,
                        stackTrace = "Test error",
                    )
                )
            )
            val history =
                ApplicationHistoryFactory.create(alluId)
                    .withEvents(
                        ApplicationHistoryFactory.createEvent(
                            newStatus = ApplicationStatus.WAITING_INFORMATION,
                            eventTime = eventTime,
                        ),
                        ApplicationHistoryFactory.createEvent(
                            newStatus = ApplicationStatus.HANDLING,
                            eventTime = eventTime.plusSeconds(10),
                        ),
                        ApplicationHistoryFactory.createEvent(
                            newStatus = ApplicationStatus.PENDING_CLIENT,
                            eventTime = eventTime.plusSeconds(20),
                        ),
                    )
                    .asList()
            every {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
            } returns emptyList()
            every {
                alluClient.getApplicationStatusHistories(listOf(alluId), eventTime.minusSeconds(1))
            } returns history
            every { alluClient.getInformationRequest(alluId) } returns
                AlluFactory.createInformationRequest(applicationAlluId = alluId)

            updateService.handleUpdates()

            assertThat(output).doesNotContain("ERROR")
            assertThat(alluEventErrorRepository.findAll()).isEmpty()
            assertThat(taydennyspyyntoRepository.findAll()).isEmpty()
            assertThat(hakemusRepository.findAll()).single().all {
                prop(HakemusEntity::alluid).isEqualTo(alluId)
                prop(HakemusEntity::alluStatus).isEqualTo(ApplicationStatus.PENDING_CLIENT)
            }
            verifySequence {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
                alluClient.getApplicationStatusHistories(listOf(alluId), eventTime.minusSeconds(1))
                alluClient.getInformationRequest(alluId)
            }
        }

        @Test
        fun `gracefully handle again failing history`(output: CapturedOutput) {
            val hanke = hankeFactory.saveMinimal()
            hakemusFactory
                .builder(USERNAME, hanke)
                .withStatus(status = ApplicationStatus.HANDLING, alluId = alluId)
                .save()
            val exception = RuntimeException("Test error")
            val eventTime = ZonedDateTime.parse("2022-09-05T14:17:16Z")
            hakemusHistoryService.saveErrors(
                listOf(
                    ApplicationHistoryFactory.createError(
                        alluId = alluId,
                        eventTime = eventTime,
                        newStatus = ApplicationStatus.WAITING_INFORMATION,
                        stackTrace = exception.stackTraceToString(),
                    )
                )
            )
            val history =
                ApplicationHistoryFactory.create(alluId)
                    .withEvents(
                        ApplicationHistoryFactory.createEvent(
                            newStatus = ApplicationStatus.WAITING_INFORMATION,
                            eventTime = eventTime,
                        ),
                        ApplicationHistoryFactory.createEvent(
                            newStatus = ApplicationStatus.HANDLING,
                            eventTime = eventTime.plusSeconds(10),
                        ),
                        ApplicationHistoryFactory.createEvent(
                            newStatus = ApplicationStatus.PENDING_CLIENT,
                            eventTime = eventTime.plusSeconds(20),
                        ),
                    )
                    .asList()
            every {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
            } returns emptyList()
            every {
                alluClient.getApplicationStatusHistories(listOf(alluId), eventTime.minusSeconds(1))
            } returns history
            every { alluClient.getInformationRequest(alluId) } throws exception

            updateService.handleUpdates()

            assertThat(output).contains("ERROR")
            assertThat(alluEventErrorRepository.findAll()).single().all {
                prop(AlluEventErrorEntity::alluId).isEqualTo(alluId)
                prop(AlluEventErrorEntity::newStatus)
                    .isEqualTo(ApplicationStatus.WAITING_INFORMATION)
                prop(AlluEventErrorEntity::applicationIdentifier)
                    .isEqualTo(DEFAULT_APPLICATION_IDENTIFIER)
                prop(AlluEventErrorEntity::eventTime).isEqualTo(eventTime)
                prop(AlluEventErrorEntity::stackTrace).isEqualTo(exception.stackTraceToString())
            }
            assertThat(taydennyspyyntoRepository.findAll()).isEmpty()
            assertThat(hakemusRepository.findAll()).single().all {
                prop(HakemusEntity::alluid).isEqualTo(alluId)
                prop(HakemusEntity::alluStatus).isEqualTo(ApplicationStatus.HANDLING)
            }
            verifySequence {
                alluClient.getApplicationStatusHistories(
                    listOf(alluId),
                    placeholderUpdateTime.toZonedDateTime(),
                )
                alluClient.getApplicationStatusHistories(listOf(alluId), eventTime.minusSeconds(1))
                alluClient.getInformationRequest(alluId)
            }
        }
    }
}
