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
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.prop
import assertk.assertions.single
import assertk.assertions.startsWith
import com.icegreen.greenmail.configuration.GreenMailConfiguration
import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.ServerSetupTest
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.ApplicationStatusEvent
import fi.hel.haitaton.hanke.allu.InformationRequestFieldKey
import fi.hel.haitaton.hanke.attachment.PDF_BYTES
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.common.MockFileClient
import fi.hel.haitaton.hanke.attachment.common.TestFile
import fi.hel.haitaton.hanke.factory.AlluFactory
import fi.hel.haitaton.hanke.factory.AlluFactory.DEFAULT_INFORMATION_REQUEST_ID
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory
import fi.hel.haitaton.hanke.factory.DateFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import fi.hel.haitaton.hanke.factory.MuutosilmoitusFactory
import fi.hel.haitaton.hanke.factory.PaatosFactory
import fi.hel.haitaton.hanke.factory.TaydennysFactory
import fi.hel.haitaton.hanke.firstReceivedMessage
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusRepository
import fi.hel.haitaton.hanke.paatos.PaatosTyyppi
import fi.hel.haitaton.hanke.permissions.Kayttooikeustaso
import fi.hel.haitaton.hanke.taydennys.TaydennysRepository
import fi.hel.haitaton.hanke.taydennys.TaydennyspyyntoEntity
import fi.hel.haitaton.hanke.taydennys.TaydennyspyyntoRepository
import fi.hel.haitaton.hanke.test.USERNAME
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verify
import io.mockk.verifySequence
import jakarta.mail.internet.MimeMessage
import java.time.ZonedDateTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension

@ExtendWith(OutputCaptureExtension::class)
class ApplicationEventServiceITest(
    @Autowired private val eventService: ApplicationEventService,
    @Autowired private val hakemusRepository: HakemusRepository,
    @Autowired private val muutosilmoitusRepository: MuutosilmoitusRepository,
    @Autowired private val taydennyspyyntoRepository: TaydennyspyyntoRepository,
    @Autowired private val taydennysRepository: TaydennysRepository,
    @Autowired private val hakemusFactory: HakemusFactory,
    @Autowired private val paatosFactory: PaatosFactory,
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

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(alluClient)
    }

    private val alluId = 42
    private val identifier = ApplicationHistoryFactory.DEFAULT_APPLICATION_IDENTIFIER

    @Test
    fun `does nothing when hakemus is not in Haitaton`() {
        assertThat(hakemusRepository.findAll()).isEmpty()

        eventService.handleApplicationEvent(
            99999,
            ApplicationStatusEvent(
                ZonedDateTime.now(),
                ApplicationStatus.PENDING,
                "JS2400001-12",
                null,
            ),
        )

        assertThat(hakemusRepository.findAll()).isEmpty()
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
        val event =
            ApplicationHistoryFactory.createEvent(
                newStatus = ApplicationStatus.REPLACED,
                applicationIdentifier = "JS2400001-13",
            )

        eventService.handleApplicationEvent(alluId, event)

        assertThat(hakemusRepository.findAll()).single().all {
            prop(HakemusEntity::alluStatus).isEqualTo(ApplicationStatus.DECISION)
            prop(HakemusEntity::applicationIdentifier).isEqualTo(originalTunnus)
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
        val event =
            ApplicationHistoryFactory.createEvent(
                newStatus = ApplicationStatus.DECISION,
                applicationIdentifier = identifier,
            )

        eventService.handleApplicationEvent(alluId, event)

        val email = greenMail.firstReceivedMessage()
        assertThat(email.allRecipients).hasSize(1)
        assertThat(email.allRecipients[0].toString()).isEqualTo(hakija.sahkoposti)
        assertThat(email.subject)
            .isEqualTo(
                "Haitaton: Johtoselvitys $identifier / Ledningsutredning $identifier / Cable report $identifier"
            )
    }

    @Test
    fun `does not send email to the contacts when a johtoselvityshakemus gets a reverted decision`() {
        val hakemus =
            hakemusFactory
                .builder()
                .withMandatoryFields()
                .withStatus(ApplicationStatus.DECISIONMAKING, alluId, identifier)
                .save()
        paatosFactory.save(hakemus, hakemus.applicationIdentifier!!)

        val event =
            ApplicationHistoryFactory.createEvent(
                newStatus = ApplicationStatus.DECISION,
                applicationIdentifier = identifier,
            )

        eventService.handleApplicationEvent(alluId, event)

        val gotEmail = greenMail.waitForIncomingEmail(1000L, 1)
        assertThat(gotEmail).isFalse()
    }

    @ParameterizedTest
    @EnumSource(ApplicationStatus::class, names = ["DECISION", "OPERATIONAL_CONDITION", "FINISHED"])
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
        val event =
            ApplicationHistoryFactory.createEvent(
                newStatus = applicationStatus,
                applicationIdentifier = identifier,
            )
        mockAlluDownload(applicationStatus)
        every { alluClient.getApplicationInformation(alluId) } returns
            AlluFactory.createAlluApplicationResponse(id = alluId, applicationId = identifier)

        eventService.handleApplicationEvent(alluId, event)

        val email = greenMail.firstReceivedMessage()
        assertThat(email.allRecipients).hasSize(1)
        assertThat(email.allRecipients[0].toString()).isEqualTo(hakija.sahkoposti)
        assertThat(email.subject)
            .isEqualTo(
                "Haitaton: Kaivuilmoitukseen KP2300001 liittyvä päätös on ladattavissa / Beslut om grävningsanmälan KP2300001 kan laddas ner / The decision concerning an excavation notification KP2300001 can be downloaded"
            )
        verifyAlluDownload(applicationStatus)
        verify { alluClient.getApplicationInformation(alluId) }
    }

    @ParameterizedTest
    @EnumSource(ApplicationStatus::class, names = ["DECISION", "OPERATIONAL_CONDITION", "FINISHED"])
    fun `does not send email to the contacts when a kaivuilmoitus gets a reverted decision`(
        applicationStatus: ApplicationStatus
    ) {
        val hakemus =
            hakemusFactory
                .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                .withMandatoryFields()
                .withStatus(ApplicationStatus.DECISIONMAKING, alluId, identifier)
                .save()
        paatosFactory.save(
            hakemus,
            hakemus.applicationIdentifier!!,
            PaatosTyyppi.valueOfApplicationStatus(applicationStatus),
        )
        val event =
            ApplicationHistoryFactory.createEvent(
                newStatus = applicationStatus,
                applicationIdentifier = identifier,
            )

        eventService.handleApplicationEvent(alluId, event)

        val gotEmail = greenMail.waitForIncomingEmail(1000L, 1)
        assertThat(gotEmail).isFalse()
    }

    @ParameterizedTest
    @EnumSource(ApplicationStatus::class, names = ["DECISION", "OPERATIONAL_CONDITION", "FINISHED"])
    fun `downloads the document when a kaivuilmoitus gets a decision`(status: ApplicationStatus) {
        val hakemus =
            hakemusFactory
                .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                .withStatus(ApplicationStatus.HANDLING, alluId, identifier)
                .save()
        val event =
            ApplicationHistoryFactory.createEvent(
                newStatus = status,
                applicationIdentifier = identifier,
            )
        mockAlluDownload(status)
        every { alluClient.getApplicationInformation(alluId) } returns
            AlluFactory.createAlluApplicationResponse()

        eventService.handleApplicationEvent(alluId, event)

        assertThat(fileClient.listBlobs(Container.PAATOKSET))
            .single()
            .prop(TestFile::path)
            .startsWith("${hakemus.id}/")
        verifyAlluDownload(status)
        verify { alluClient.getApplicationInformation(alluId) }
    }

    @ParameterizedTest
    @EnumSource(ApplicationStatus::class, names = ["DECISION", "OPERATIONAL_CONDITION", "FINISHED"])
    fun `does not download the document when a kaivuilmoitus gets a reverted decision`(
        status: ApplicationStatus
    ) {
        val hakemus =
            hakemusFactory
                .builder(ApplicationType.EXCAVATION_NOTIFICATION)
                .withMandatoryFields()
                .withStatus(ApplicationStatus.DECISIONMAKING, alluId, identifier)
                .save()
        paatosFactory.save(
            hakemus,
            hakemus.applicationIdentifier!!,
            PaatosTyyppi.valueOfApplicationStatus(status),
        )
        val event =
            ApplicationHistoryFactory.createEvent(
                newStatus = status,
                applicationIdentifier = identifier,
            )

        eventService.handleApplicationEvent(alluId, event)

        verify { alluClient wasNot Called }
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
        val event =
            ApplicationHistoryFactory.createEvent(
                newStatus = ApplicationStatus.DECISION,
                applicationIdentifier = identifier,
            )
        if (applicationType == ApplicationType.EXCAVATION_NOTIFICATION) {
            mockAlluDownload(ApplicationStatus.DECISION)
            every { alluClient.getApplicationInformation(alluId) } returns
                AlluFactory.createAlluApplicationResponse()
        }

        eventService.handleApplicationEvent(alluId, event)

        val hakemus = hakemusRepository.findOneById(muutosilmoitus.hakemusId)!!.hakemusEntityData
        assertThat(hakemus.endTime).isEqualTo(muutosilmoitus.hakemusData.endTime)
        assertThat(muutosilmoitusRepository.findAll()).isEmpty()
        if (applicationType == ApplicationType.EXCAVATION_NOTIFICATION) {
            verifyAlluDownload(ApplicationStatus.DECISION)
            verify { alluClient.getApplicationInformation(alluId) }
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
        val event =
            ApplicationHistoryFactory.createEvent(
                newStatus = ApplicationStatus.WAITING_INFORMATION,
                applicationIdentifier = identifier,
            )
        every { alluClient.getInformationRequest(alluId) } returns
            AlluFactory.createInformationRequest(applicationAlluId = alluId)

        eventService.handleApplicationEvent(alluId, event)

        val hakemus = hakemusRepository.findOneById(muutosilmoitus.hakemusId)!!.hakemusEntityData
        assertThat(hakemus.name).isEqualTo(newName)
        assertThat(muutosilmoitusRepository.findAll()).isEmpty()
        verifySequence { alluClient.getInformationRequest(alluId) }
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
                newStatus = ApplicationStatus.WAITING_INFORMATION,
                applicationIdentifier = identifier,
            )
        every { alluClient.getInformationRequest(alluId) } returns
            AlluFactory.createInformationRequest()

        eventService.handleApplicationEvent(alluId, event)

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
                newStatus = ApplicationStatus.WAITING_INFORMATION,
                applicationIdentifier = identifier,
            )
        every { alluClient.getInformationRequest(alluId) } returns
            AlluFactory.createInformationRequest(applicationAlluId = alluId)

        eventService.handleApplicationEvent(alluId, event)

        assertThat(taydennyspyyntoRepository.findAll()).single().all {
            prop(TaydennyspyyntoEntity::alluId).isEqualTo(DEFAULT_INFORMATION_REQUEST_ID)
            prop(TaydennyspyyntoEntity::applicationId).isEqualTo(hakemus.id)
            prop(TaydennyspyyntoEntity::kentat)
                .containsOnly(
                    InformationRequestFieldKey.OTHER to
                        AlluFactory.DEFAULT_INFORMATION_REQUEST_DESCRIPTION
                )
        }
        verifySequence { alluClient.getInformationRequest(alluId) }
    }

    @Test
    fun `doesn't send email or save the taydennyspyynto when the new status is WAITING_INFORMATION but Allu doesn't have the taydennyspyynto`() {
        hakemusFactory.builder().withStatus(ApplicationStatus.HANDLING, alluId, identifier).save()
        val event =
            ApplicationHistoryFactory.createEvent(
                newStatus = ApplicationStatus.WAITING_INFORMATION,
                applicationIdentifier = identifier,
            )
        every { alluClient.getInformationRequest(alluId) } returns null

        eventService.handleApplicationEvent(alluId, event)

        val emails = greenMail.receivedMessages
        assertThat(emails).isEmpty()
        assertThat(taydennyspyyntoRepository.findAll()).isEmpty()
        verifySequence { alluClient.getInformationRequest(alluId) }
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
        val event =
            ApplicationHistoryFactory.createEvent(
                newStatus = ApplicationStatus.HANDLING,
                applicationIdentifier = identifier,
            )

        eventService.handleApplicationEvent(alluId, event)

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
        val event =
            ApplicationHistoryFactory.createEvent(
                newStatus = ApplicationStatus.HANDLING,
                applicationIdentifier = identifier,
            )

        eventService.handleApplicationEvent(alluId, event)

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
    }

    @Test
    fun `updates the status when the new status is HANDLING and there is no taydennyspyynto`(
        output: CapturedOutput
    ) {
        hakemusFactory.builder().withStatus(ApplicationStatus.WAITING_INFORMATION, 42).save()
        val event =
            ApplicationHistoryFactory.createEvent(
                newStatus = ApplicationStatus.HANDLING,
                applicationIdentifier = identifier,
            )

        eventService.handleApplicationEvent(alluId, event)

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
                newStatus = ApplicationStatus.HANDLING,
                applicationIdentifier = identifier,
            )

        eventService.handleApplicationEvent(alluId, event)

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
