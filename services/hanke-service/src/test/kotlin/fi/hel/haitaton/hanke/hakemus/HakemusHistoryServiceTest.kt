package fi.hel.haitaton.hanke.hakemus

import assertk.all
import assertk.assertThat
import assertk.assertions.contains
import fi.hel.haitaton.hanke.allu.AlluStatus
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
import java.time.OffsetDateTime
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
    private val taydennysService: TaydennysService = mockk()
    private val paatosService: PaatosService = mockk()
    private val hankeKayttajaService: HankeKayttajaService = mockk(relaxUnitFun = true)
    private val publisher: ApplicationEventPublisher = mockk()

    private val historyService: HakemusHistoryService =
        HakemusHistoryService(
            hakemusRepository,
            alluStatusRepository,
            taydennysService,
            paatosService,
            hankeKayttajaService,
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
            publisher,
        )
    }

    @Nested
    @ExtendWith(OutputCaptureExtension::class)
    inner class HandleApplicationUpdates {
        private val alluid = 42
        private val applicationId = 13L
        private val hankeTunnus = "HAI23-1"
        private val receiver = HakemusyhteyshenkiloFactory.DEFAULT_SAHKOPOSTI
        private val updateTime = OffsetDateTime.parse("2022-10-09T06:36:51Z")
        private val identifier = ApplicationHistoryFactory.DEFAULT_APPLICATION_IDENTIFIER

        @Test
        fun `sends email to the contacts when hakemus gets a decision`() {
            every { hakemusRepository.getOneByAlluid(42) } returns applicationEntityWithCustomer()
            justRun {
                publisher.publishEvent(
                    JohtoselvitysCompleteEmail(receiver, applicationId, identifier))
            }
            every { hakemusRepository.save(any()) } answers { firstArg() }
            every { alluStatusRepository.getReferenceById(1) } returns AlluStatus(1, updateTime)

            historyService.handleHakemusUpdates(createHistories(), updateTime)

            verifySequence {
                hakemusRepository.getOneByAlluid(42)
                publisher.publishEvent(
                    JohtoselvitysCompleteEmail(receiver, applicationId, identifier))
                hakemusRepository.save(any())
                alluStatusRepository.getReferenceById(1)
            }
        }

        @ParameterizedTest
        @EnumSource(
            ApplicationStatus::class, names = ["DECISION", "OPERATIONAL_CONDITION", "FINISHED"])
        fun `sends email to the contacts when a kaivuilmoitus gets a decision`(
            applicationStatus: ApplicationStatus
        ) {
            every { hakemusRepository.getOneByAlluid(42) } returns
                applicationEntityWithCustomer(type = ApplicationType.EXCAVATION_NOTIFICATION)
            justRun {
                publisher.publishEvent(
                    KaivuilmoitusDecisionEmail(receiver, applicationId, identifier))
            }
            every { hakemusRepository.save(any()) } answers { firstArg() }
            every { alluStatusRepository.getReferenceById(1) } returns AlluStatus(1, updateTime)

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

            historyService.handleHakemusUpdates(createHistories(applicationStatus), updateTime)

            verifySequence {
                hakemusRepository.getOneByAlluid(42)
                publisher.publishEvent(
                    KaivuilmoitusDecisionEmail(receiver, applicationId, identifier))
                saveMethod(any(), any())
                hakemusRepository.save(any())
                alluStatusRepository.getReferenceById(1)
            }
        }

        @Test
        fun `doesn't send email when status is not decision`() {
            every { hakemusRepository.getOneByAlluid(42) } returns applicationEntityWithCustomer()
            every { hakemusRepository.save(any()) } answers { firstArg() }
            every { alluStatusRepository.getReferenceById(1) } returns AlluStatus(1, updateTime)

            val histories =
                listOf(
                    ApplicationHistoryFactory.create(
                        alluid,
                        ApplicationHistoryFactory.createEvent(
                            applicationIdentifier = identifier,
                            newStatus = ApplicationStatus.DECISIONMAKING)),
                )

            historyService.handleHakemusUpdates(histories, updateTime)

            verifySequence {
                hakemusRepository.getOneByAlluid(42)
                hakemusRepository.save(any())
                alluStatusRepository.getReferenceById(1)
            }
        }

        @Test
        fun `logs error when there are no receivers`(output: CapturedOutput) {
            every { hakemusRepository.getOneByAlluid(42) } returns
                applicationEntityWithoutCustomer()
            every { hakemusRepository.save(any()) } answers { firstArg() }
            every { alluStatusRepository.getReferenceById(1) } returns AlluStatus(1, updateTime)

            historyService.handleHakemusUpdates(createHistories(), updateTime)

            assertThat(output)
                .contains("No receivers found for hakemus DECISION ready email, not sending any.")
            verifySequence {
                hakemusRepository.getOneByAlluid(42)
                hakemusRepository.save(any())
                alluStatusRepository.getReferenceById(1)
            }
        }

        @ParameterizedTest
        @EnumSource(ApplicationStatus::class, names = ["OPERATIONAL_CONDITION", "FINISHED"])
        fun `logs an error when a johtoselvityshakemus gets a supervision document`(
            status: ApplicationStatus,
            output: CapturedOutput
        ) {
            every { hakemusRepository.getOneByAlluid(alluid) } returns
                applicationEntityWithoutCustomer()
            every { hakemusRepository.save(any()) } answers { firstArg() }
            every { alluStatusRepository.getReferenceById(1) } returns AlluStatus(1, updateTime)

            historyService.handleHakemusUpdates(createHistories(status), updateTime)

            assertThat(output).all {
                contains("Got $status update for a cable report.")
                contains("id=$applicationId")
                contains("alluId=$alluid")
                contains("identifier=$identifier")
            }
            verifySequence {
                hakemusRepository.getOneByAlluid(42)
                hakemusRepository.save(any())
                alluStatusRepository.getReferenceById(1)
            }
        }

        private fun applicationEntityWithoutCustomer(
            id: Long = applicationId,
            type: ApplicationType = ApplicationType.CABLE_REPORT
        ): HakemusEntity {
            val entity =
                HakemusFactory.createEntity(
                    id = id,
                    alluid = alluid,
                    applicationIdentifier = identifier,
                    userId = USERNAME,
                    hanke = HankeFactory.createMinimalEntity(id = 1, hankeTunnus = hankeTunnus),
                    applicationType = type,
                )
            return entity
        }

        private fun applicationEntityWithCustomer(
            id: Long = applicationId,
            type: ApplicationType = ApplicationType.CABLE_REPORT
        ): HakemusEntity {
            val entity = applicationEntityWithoutCustomer(id, type)
            entity.yhteystiedot[ApplicationContactType.HAKIJA] =
                HakemusyhteystietoFactory.createEntity(application = entity, sahkoposti = receiver)
                    .withYhteyshenkilo(
                        permission = PermissionFactory.createEntity(userId = USERNAME))
            return entity
        }

        private fun createHistories(status: ApplicationStatus = ApplicationStatus.DECISION) =
            listOf(
                ApplicationHistoryFactory.create(
                    alluid,
                    ApplicationHistoryFactory.createEvent(
                        applicationIdentifier = identifier,
                        newStatus = status,
                    )),
            )
    }
}
