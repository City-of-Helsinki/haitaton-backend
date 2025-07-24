package fi.hel.haitaton.hanke.hakemus

import assertk.assertThat
import assertk.assertions.isEqualTo
import fi.hel.haitaton.hanke.allu.AlluEventRepository
import fi.hel.haitaton.hanke.allu.AlluEventStatus
import fi.hel.haitaton.hanke.allu.AlluStatusRepository
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.factory.AlluEventFactory
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory
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

class HakemusHistoryServiceTest {
    private val hakemusRepository: HakemusRepository = mockk()
    private val alluStatusRepository: AlluStatusRepository = mockk()
    private val alluEventRepository: AlluEventRepository = mockk()
    private val applicationEventService: ApplicationEventService = mockk()

    private val historyService: HakemusHistoryService =
        HakemusHistoryService(
            hakemusRepository,
            alluStatusRepository,
            alluEventRepository,
            applicationEventService,
        )

    private val alluId = 42

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
            alluEventRepository,
            applicationEventService,
        )
    }

    @Nested
    inner class ProcessApplicationHistories {

        @Test
        fun `saves application history events as PENDING`() {
            val event =
                ApplicationHistoryFactory.createEvent(newStatus = ApplicationStatus.DECISION)
            val history = ApplicationHistoryFactory.create(alluId, event)
            val entity = AlluEventFactory.createEntity(alluId, event, AlluEventStatus.PENDING)
            justRun { alluEventRepository.batchInsertIgnoreDuplicates(listOf(entity)) }
            every { alluEventRepository.findByStatusInOrderByAlluIdAscEventTimeAsc(any()) } returns
                emptyList()

            historyService.processApplicationHistories(listOf(history))

            verifySequence {
                alluEventRepository.batchInsertIgnoreDuplicates(listOf(entity))
                alluEventRepository.findByStatusInOrderByAlluIdAscEventTimeAsc(any())
            }
        }
    }

    @Nested
    inner class ProcessApplicationEvents {

        @Test
        fun `marks successfully handled failed event as PROCESSED`() {
            val event =
                ApplicationHistoryFactory.createEvent(newStatus = ApplicationStatus.DECISION)
            val entity = AlluEventFactory.createEntity(alluId, event, AlluEventStatus.FAILED)
            every { alluEventRepository.findByStatusInOrderByAlluIdAscEventTimeAsc(any()) } returns
                listOf(entity)
            justRun { applicationEventService.handleApplicationEvent(alluId, event) }

            historyService.processApplicationHistories(emptyList())

            verifySequence {
                alluEventRepository.findByStatusInOrderByAlluIdAscEventTimeAsc(any())
                applicationEventService.handleApplicationEvent(alluId, event)
            }
            assertThat(entity.status).isEqualTo(AlluEventStatus.PROCESSED)
        }

        @Test
        fun `does not change status for failing retry`() {
            val event =
                ApplicationHistoryFactory.createEvent(newStatus = ApplicationStatus.DECISION)
            val entity = AlluEventFactory.createEntity(alluId, event, AlluEventStatus.FAILED)
            every { alluEventRepository.findByStatusInOrderByAlluIdAscEventTimeAsc(any()) } returns
                listOf(entity)
            every { applicationEventService.handleApplicationEvent(alluId, event) } throws
                RuntimeException("Test failure")

            historyService.processApplicationHistories(emptyList())

            verifySequence {
                alluEventRepository.findByStatusInOrderByAlluIdAscEventTimeAsc(any())
                applicationEventService.handleApplicationEvent(alluId, event)
            }
            assertThat(entity.status).isEqualTo(AlluEventStatus.FAILED)
        }

        @Test
        fun `handles next pending event after retry`() {
            val event1 =
                ApplicationHistoryFactory.createEvent(newStatus = ApplicationStatus.DECISIONMAKING)
            val event2 =
                ApplicationHistoryFactory.createEvent(newStatus = ApplicationStatus.DECISION)
            val entity1 = AlluEventFactory.createEntity(alluId, event1, AlluEventStatus.FAILED)
            val entity2 = AlluEventFactory.createEntity(alluId, event2, AlluEventStatus.PENDING)
            every { alluEventRepository.findByStatusInOrderByAlluIdAscEventTimeAsc(any()) } returns
                listOf(entity1, entity2)
            justRun { applicationEventService.handleApplicationEvent(alluId, event1) }
            justRun { applicationEventService.handleApplicationEvent(alluId, event2) }

            historyService.processApplicationHistories(emptyList())

            verifySequence {
                alluEventRepository.findByStatusInOrderByAlluIdAscEventTimeAsc(any())
                applicationEventService.handleApplicationEvent(alluId, event1)
                applicationEventService.handleApplicationEvent(alluId, event2)
            }
            assertThat(entity1.status).isEqualTo(AlluEventStatus.PROCESSED)
            assertThat(entity2.status).isEqualTo(AlluEventStatus.PROCESSED)
        }

        @Test
        fun `marks successfully handled pending event as PROCESSED`() {
            val event =
                ApplicationHistoryFactory.createEvent(newStatus = ApplicationStatus.DECISION)
            val entity = AlluEventFactory.createEntity(alluId, event, AlluEventStatus.PENDING)
            every { alluEventRepository.findByStatusInOrderByAlluIdAscEventTimeAsc(any()) } returns
                listOf(entity)
            justRun { applicationEventService.handleApplicationEvent(alluId, event) }

            historyService.processApplicationHistories(emptyList())

            verifySequence {
                alluEventRepository.findByStatusInOrderByAlluIdAscEventTimeAsc(any())
                applicationEventService.handleApplicationEvent(alluId, event)
            }
            assertThat(entity.status).isEqualTo(AlluEventStatus.PROCESSED)
        }

        @Test
        fun `marks failing pending event as FAILED`() {
            val event =
                ApplicationHistoryFactory.createEvent(newStatus = ApplicationStatus.DECISION)
            val entity = AlluEventFactory.createEntity(alluId, event, AlluEventStatus.PENDING)
            every { alluEventRepository.findByStatusInOrderByAlluIdAscEventTimeAsc(any()) } returns
                listOf(entity)
            every { applicationEventService.handleApplicationEvent(alluId, event) } throws
                RuntimeException("Test failure")

            historyService.processApplicationHistories(emptyList())

            verifySequence {
                alluEventRepository.findByStatusInOrderByAlluIdAscEventTimeAsc(any())
                applicationEventService.handleApplicationEvent(alluId, event)
            }
            assertThat(entity.status).isEqualTo(AlluEventStatus.FAILED)
        }
    }
}
