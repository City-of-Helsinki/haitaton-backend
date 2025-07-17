package fi.hel.haitaton.hanke.hakemus

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.prop
import com.icegreen.greenmail.configuration.GreenMailConfiguration
import com.icegreen.greenmail.junit5.GreenMailExtension
import com.icegreen.greenmail.util.ServerSetupTest
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.allu.AlluEventEntity
import fi.hel.haitaton.hanke.allu.AlluEventRepository
import fi.hel.haitaton.hanke.allu.AlluEventStatus
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.findPendingAndFailedEventsGrouped
import fi.hel.haitaton.hanke.factory.AlluEventFactory
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.findByOrderByAlluIdAscEventTimeAsc
import fi.hel.haitaton.hanke.test.Asserts.isGreaterThan
import fi.hel.haitaton.hanke.test.USERNAME
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verifySequence
import java.time.ZonedDateTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.system.OutputCaptureExtension

class HakemusHistoryServiceITest(
    @Autowired private val historyService: HakemusHistoryService,
    @Autowired private val alluEventRepository: AlluEventRepository,
    @Autowired private val alluClient: AlluClient,
    @Autowired private val alluEventFactory: AlluEventFactory,
    @Autowired private val hakemusFactory: HakemusFactory,
) : IntegrationTest() {

    companion object {
        @JvmField
        @RegisterExtension
        val greenMail: GreenMailExtension =
            GreenMailExtension(ServerSetupTest.SMTP)
                .withConfiguration(GreenMailConfiguration.aConfig().withDisabledAuthentication())
    }

    private val alluId = 42

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
    @ExtendWith(OutputCaptureExtension::class)
    inner class ProcessApplicationHistories {

        @Test
        fun `processes pending events in correct order`() {
            val eventTime = ZonedDateTime.now(TZ_UTC)
            alluEventFactory.saveEventEntity(
                alluId,
                ApplicationHistoryFactory.createEvent(
                    eventTime,
                    ApplicationStatus.WAITING_INFORMATION,
                ),
            )
            alluEventFactory.saveEventEntity(
                alluId,
                ApplicationHistoryFactory.createEvent(
                    eventTime.minusDays(1),
                    ApplicationStatus.HANDLING,
                ),
            )
            alluEventFactory.saveEventEntity(
                alluId,
                ApplicationHistoryFactory.createEvent(
                    eventTime.minusDays(2),
                    ApplicationStatus.PENDING,
                ),
            )
            alluEventFactory.saveEventEntity(
                alluId,
                ApplicationHistoryFactory.createEvent(
                    eventTime.plusDays(1),
                    ApplicationStatus.INFORMATION_RECEIVED,
                ),
            )
            alluEventFactory.saveEventEntity(
                alluId,
                ApplicationHistoryFactory.createEvent(
                    eventTime.plusDays(2),
                    ApplicationStatus.DECISION,
                ),
            )
            var entities = alluEventRepository.findPendingAndFailedEventsGrouped()[alluId]!!
            assertThat(entities).hasSize(5)
            assertThat(entities[0].newStatus).isEqualTo(ApplicationStatus.PENDING)
            assertThat(entities[1].newStatus).isEqualTo(ApplicationStatus.HANDLING)
            assertThat(entities[2].newStatus).isEqualTo(ApplicationStatus.WAITING_INFORMATION)
            assertThat(entities[3].newStatus).isEqualTo(ApplicationStatus.INFORMATION_RECEIVED)
            assertThat(entities[4].newStatus).isEqualTo(ApplicationStatus.DECISION)

            historyService.processApplicationHistories(emptyList())

            entities =
                alluEventRepository.findByStatusInOrderByAlluIdAscEventTimeAsc(
                    listOf(AlluEventStatus.PROCESSED)
                )
            assertThat(entities).hasSize(5)
            assertThat(entities[1].processedAt).isGreaterThan(entities[0].processedAt)
            assertThat(entities[2].processedAt).isGreaterThan(entities[1].processedAt)
            assertThat(entities[3].processedAt).isGreaterThan(entities[2].processedAt)
            assertThat(entities[4].processedAt).isGreaterThan(entities[3].processedAt)
        }

        @Test
        fun `marks handled event as PROCESSED`() {
            val event =
                ApplicationHistoryFactory.createEvent(newStatus = ApplicationStatus.HANDLING)
            val history = ApplicationHistoryFactory.create(alluId, event)

            historyService.processApplicationHistories(listOf(history))

            val entity = alluEventRepository.findAll().single()
            assertThat(entity).all {
                prop(AlluEventEntity::alluId).isEqualTo(alluId)
                prop(AlluEventEntity::eventTime).isEqualTo(event.eventTime.toOffsetDateTime())
                prop(AlluEventEntity::newStatus).isEqualTo(event.newStatus)
                prop(AlluEventEntity::applicationIdentifier).isEqualTo(event.applicationIdentifier)
                prop(AlluEventEntity::status).isEqualTo(AlluEventStatus.PROCESSED)
            }
        }

        @Test
        fun `marks successfully handled failed event as PROCESSED`() {
            val event =
                ApplicationHistoryFactory.createEvent(newStatus = ApplicationStatus.HANDLING)
            alluEventFactory.saveEventEntity(alluId, event, AlluEventStatus.FAILED)

            historyService.processApplicationHistories(emptyList())

            val entity = alluEventRepository.findAll().single()
            assertThat(entity.status).isEqualTo(AlluEventStatus.PROCESSED)
        }

        @Test
        fun `does not change status for failing event retry`() {
            hakemusFactory
                .builder(USERNAME, applicationType = ApplicationType.EXCAVATION_NOTIFICATION)
                .withStatus(ApplicationStatus.DECISIONMAKING, alluId)
                .saveEntity()
            val event =
                ApplicationHistoryFactory.createEvent(newStatus = ApplicationStatus.DECISION)
            alluEventFactory.saveEventEntity(alluId, event, AlluEventStatus.FAILED)
            every { alluClient.getDecisionPdf(alluId) } throws RuntimeException("Test failure")

            historyService.processApplicationHistories(emptyList())

            val entity = alluEventRepository.findAll().single()
            assertThat(entity.status).isEqualTo(AlluEventStatus.FAILED)
            verifySequence { alluClient.getDecisionPdf(alluId) }
        }

        @Test
        fun `handles next pending event after successful retry`() {
            val event1 =
                ApplicationHistoryFactory.createEvent(newStatus = ApplicationStatus.PENDING)
            val event2 =
                ApplicationHistoryFactory.createEvent(newStatus = ApplicationStatus.HANDLING)
            alluEventFactory.saveEventEntity(alluId, event1, AlluEventStatus.FAILED)
            alluEventFactory.saveEventEntity(alluId, event2, AlluEventStatus.PENDING)

            historyService.processApplicationHistories(emptyList())

            val entities = alluEventRepository.findByOrderByAlluIdAscEventTimeAsc()
            assertThat(entities).hasSize(2)
            assertThat(entities[0].status).isEqualTo(AlluEventStatus.PROCESSED)
            assertThat(entities[1].status).isEqualTo(AlluEventStatus.PROCESSED)
        }

        @Test
        fun `marks successfully handled pending event as PROCESSED`() {
            val event =
                ApplicationHistoryFactory.createEvent(newStatus = ApplicationStatus.HANDLING)
            alluEventFactory.saveEventEntity(alluId, event, AlluEventStatus.PENDING)

            historyService.processApplicationHistories(emptyList())

            val entity = alluEventRepository.findAll().single()
            assertThat(entity.status).isEqualTo(AlluEventStatus.PROCESSED)
        }

        @Test
        fun `marks failing pending event as FAILED`() {
            hakemusFactory
                .builder(USERNAME, applicationType = ApplicationType.EXCAVATION_NOTIFICATION)
                .withStatus(ApplicationStatus.DECISIONMAKING, alluId)
                .saveEntity()
            val event =
                ApplicationHistoryFactory.createEvent(newStatus = ApplicationStatus.DECISION)
            alluEventFactory.saveEventEntity(alluId, event, AlluEventStatus.PENDING)
            every { alluClient.getDecisionPdf(alluId) } throws RuntimeException("Test failure")

            historyService.processApplicationHistories(emptyList())

            val entity = alluEventRepository.findAll().single()
            assertThat(entity.status).isEqualTo(AlluEventStatus.FAILED)
            verifySequence { alluClient.getDecisionPdf(alluId) }
        }
    }

    @Nested
    inner class DeleteOldProcessedEvents {

        @Test
        fun `deletes old processed events`() {
            val eventTime = ZonedDateTime.now(TZ_UTC).minusDays(99)
            val event1 =
                ApplicationHistoryFactory.createEvent(
                    eventTime = eventTime.minusDays(1),
                    newStatus = ApplicationStatus.PENDING,
                )
            val event2 =
                ApplicationHistoryFactory.createEvent(
                    eventTime = eventTime,
                    newStatus = ApplicationStatus.HANDLING,
                )
            alluEventFactory.saveEventEntity(alluId, event1, AlluEventStatus.PROCESSED)
            alluEventFactory.saveEventEntity(alluId, event2, AlluEventStatus.PENDING)

            historyService.deleteOldProcessedEvents(90)

            val entities = alluEventRepository.findByOrderByAlluIdAscEventTimeAsc()
            assertThat(entities).hasSize(1)
            assertThat(entities[0].status).isEqualTo(AlluEventStatus.PENDING)
        }
    }
}
