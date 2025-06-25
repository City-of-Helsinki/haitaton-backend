package fi.hel.haitaton.hanke.hakemus

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import fi.hel.haitaton.hanke.allu.AlluEventErrorRepository
import fi.hel.haitaton.hanke.allu.AlluStatusRepository
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.email.JohtoselvitysCompleteEmail
import fi.hel.haitaton.hanke.email.KaivuilmoitusDecisionEmail
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteyshenkiloFactory
import fi.hel.haitaton.hanke.factory.HakemusyhteyshenkiloFactory.withYhteyshenkilo
import fi.hel.haitaton.hanke.factory.HakemusyhteystietoFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.PermissionFactory
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusService
import fi.hel.haitaton.hanke.paatos.PaatosService
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.taydennys.TaydennysService
import fi.hel.haitaton.hanke.test.USERNAME
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verifySequence
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import org.springframework.context.ApplicationEventPublisher

class HakemusHistoryServiceTest {
    private val hakemusRepository: HakemusRepository = mockk()
    private val alluStatusRepository: AlluStatusRepository = mockk()
    private val alluEventErrorRepository: AlluEventErrorRepository = mockk()
    private val taydennysService: TaydennysService = mockk()
    private val paatosService: PaatosService = mockk()
    private val hankeKayttajaService: HankeKayttajaService = mockk(relaxUnitFun = true)
    private val muutosilmoitusService: MuutosilmoitusService = mockk(relaxUnitFun = true)
    private val publisher: ApplicationEventPublisher = mockk()

    private val historyService: HakemusHistoryService =
        HakemusHistoryService(
            hakemusRepository,
            alluStatusRepository,
            alluEventErrorRepository,
            taydennysService,
            paatosService,
            hankeKayttajaService,
            muutosilmoitusService,
            publisher,
        )

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(
            hakemusRepository,
            alluStatusRepository,
            taydennysService,
            paatosService,
            hankeKayttajaService,
            muutosilmoitusService,
            publisher,
        )
    }

    @Nested
    @ExtendWith(OutputCaptureExtension::class)
    inner class HandleApplicationUpdates {
        private val alluId = 42
        private val applicationId = 13L
        private val hankeTunnus = "HAI23-1"
        private val receiver = HakemusyhteyshenkiloFactory.DEFAULT_SAHKOPOSTI
        private val identifier = ApplicationHistoryFactory.DEFAULT_APPLICATION_IDENTIFIER

        @Test
        fun `sends email to the contacts when a johtoselvityshakemus gets a decision`() {
            every { hakemusRepository.getOneByAlluid(alluId) } returns
                applicationEntityWithCustomer()
            justRun {
                publisher.publishEvent(
                    JohtoselvitysCompleteEmail(receiver, applicationId, identifier)
                )
            }

            historyService.handleApplicationEvent(alluId, createEvent())

            verifySequence {
                hakemusRepository.getOneByAlluid(alluId)
                muutosilmoitusService.mergeMuutosilmoitusToHakemusIfItExists(any())
                publisher.publishEvent(
                    JohtoselvitysCompleteEmail(receiver, applicationId, identifier)
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
            every { hakemusRepository.getOneByAlluid(alluId) } returns
                applicationEntityWithCustomer(type = ApplicationType.EXCAVATION_NOTIFICATION)
            justRun {
                publisher.publishEvent(
                    KaivuilmoitusDecisionEmail(receiver, applicationId, identifier)
                )
            }

            val saveMethod =
                when (applicationStatus) {
                    ApplicationStatus.DECISION -> {
                        paatosService::saveKaivuilmoituksenPaatos
                    }
                    ApplicationStatus.OPERATIONAL_CONDITION ->
                        paatosService::saveKaivuilmoituksenToiminnallinenKunto
                    else -> paatosService::saveKaivuilmoituksenTyoValmis
                }
            justRun { saveMethod(any(), any()) }

            historyService.handleApplicationEvent(alluId, createEvent(applicationStatus))

            verifySequence {
                hakemusRepository.getOneByAlluid(42)
                if (applicationStatus == ApplicationStatus.DECISION) {
                    muutosilmoitusService.mergeMuutosilmoitusToHakemusIfItExists(any())
                }
                publisher.publishEvent(
                    KaivuilmoitusDecisionEmail(receiver, applicationId, identifier)
                )
                saveMethod(any(), any())
            }
        }

        @Test
        fun `doesn't send email when status is not decision`() {
            every { hakemusRepository.getOneByAlluid(alluId) } returns
                applicationEntityWithCustomer()

            val event =
                ApplicationHistoryFactory.createEvent(
                    applicationIdentifier = identifier,
                    newStatus = ApplicationStatus.DECISIONMAKING,
                )

            historyService.handleApplicationEvent(alluId, event)

            verifySequence { hakemusRepository.getOneByAlluid(42) }
        }

        @Test
        fun `logs error when there are no receivers`(output: CapturedOutput) {
            every { hakemusRepository.getOneByAlluid(alluId) } returns
                applicationEntityWithoutCustomer()

            historyService.handleApplicationEvent(alluId, createEvent())

            assertThat(output)
                .contains("No receivers found for hakemus DECISION ready email, not sending any.")
            verifySequence {
                hakemusRepository.getOneByAlluid(42)
                muutosilmoitusService.mergeMuutosilmoitusToHakemusIfItExists(any())
            }
        }

        @ParameterizedTest
        @EnumSource(ApplicationStatus::class, names = ["OPERATIONAL_CONDITION", "FINISHED"])
        fun `logs an error when a johtoselvityshakemus gets a supervision document`(
            status: ApplicationStatus,
            output: CapturedOutput,
        ) {
            every { hakemusRepository.getOneByAlluid(alluId) } returns
                applicationEntityWithoutCustomer()

            historyService.handleApplicationEvent(alluId, createEvent(status))

            assertThat(output).all {
                contains("Got $status update for a cable report.")
                contains("id=$applicationId")
                contains("alluId=$alluId")
                contains("identifier=$identifier")
            }
            verifySequence { hakemusRepository.getOneByAlluid(42) }
        }

        private fun applicationEntityWithoutCustomer(
            id: Long = applicationId,
            type: ApplicationType = ApplicationType.CABLE_REPORT,
        ): HakemusEntity {
            val entity =
                HakemusFactory.createEntity(
                    id = id,
                    alluid = alluId,
                    applicationIdentifier = identifier,
                    userId = USERNAME,
                    hanke = HankeFactory.createMinimalEntity(id = 1, hankeTunnus = hankeTunnus),
                    applicationType = type,
                )
            return entity
        }

        private fun applicationEntityWithCustomer(
            id: Long = applicationId,
            type: ApplicationType = ApplicationType.CABLE_REPORT,
        ): HakemusEntity {
            val entity = applicationEntityWithoutCustomer(id, type)
            entity.yhteystiedot[ApplicationContactType.HAKIJA] =
                HakemusyhteystietoFactory.createEntity(application = entity, sahkoposti = receiver)
                    .withYhteyshenkilo(
                        permission = PermissionFactory.createEntity(userId = USERNAME)
                    )
            return entity
        }

        private fun createEvent(status: ApplicationStatus = ApplicationStatus.DECISION) =
            ApplicationHistoryFactory.createEvent(
                applicationIdentifier = identifier,
                newStatus = status,
            )
    }
}
