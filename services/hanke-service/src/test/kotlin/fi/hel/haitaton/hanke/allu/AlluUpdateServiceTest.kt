package fi.hel.haitaton.hanke.allu

import assertk.assertThat
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory
import fi.hel.haitaton.hanke.test.Asserts.isRecent
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
class AlluUpdateServiceTest {
    private val applicationRepository: ApplicationRepository = mockk()
    private val alluStatusRepository: AlluStatusRepository = mockk()
    private val cableReportService: CableReportService = mockk()
    private val applicationService: ApplicationService = mockk()

    private val alluUpdateService =
        AlluUpdateService(
            applicationRepository,
            alluStatusRepository,
            cableReportService,
            applicationService
        )

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun confirmMocks() {
        confirmVerified(
            alluStatusRepository,
            applicationRepository,
            cableReportService,
            applicationService
        )
    }

    @Nested
    inner class CheckApplicationStatuses {
        @Test
        fun `does nothing with no alluids`() {
            every { applicationRepository.getAllAlluIds() } returns listOf()

            alluUpdateService.checkApplicationStatuses()

            verify {
                applicationRepository.getAllAlluIds()
                alluStatusRepository wasNot Called
                cableReportService wasNot Called
                applicationService wasNot Called
            }
        }

        @Test
        fun `calls Allu with alluids and last update date`() {
            val alluids = listOf(23, 24)
            val lastUpdatedString = "2022-10-12T14:14:58.423521Z"
            val lastUpdated = OffsetDateTime.parse(lastUpdatedString)
            val eventsAfter = ZonedDateTime.parse(lastUpdatedString)
            every { applicationRepository.getAllAlluIds() } returns alluids
            every { alluStatusRepository.getLastUpdateTime() } returns lastUpdated
            every { cableReportService.getApplicationStatusHistories(alluids, eventsAfter) } returns
                listOf()
            justRun { applicationService.handleApplicationUpdates(listOf(), any()) }

            alluUpdateService.checkApplicationStatuses()

            verify {
                applicationRepository.getAllAlluIds()
                alluStatusRepository.getLastUpdateTime()
                cableReportService.getApplicationStatusHistories(alluids, eventsAfter)
                applicationService.handleApplicationUpdates(listOf(), any())
            }
        }

        @Test
        fun `calls application service with the returned histories`() {
            val alluids = listOf(23, 24)
            val lastUpdatedString = "2022-10-12T14:14:58.423521Z"
            val lastUpdated = OffsetDateTime.parse(lastUpdatedString)
            val eventsAfter = ZonedDateTime.parse(lastUpdatedString)
            val histories = listOf(ApplicationHistoryFactory.create(applicationId = 24))
            every { applicationRepository.getAllAlluIds() } returns alluids
            every { alluStatusRepository.getLastUpdateTime() } returns lastUpdated
            every { cableReportService.getApplicationStatusHistories(alluids, eventsAfter) } returns
                histories
            justRun { applicationService.handleApplicationUpdates(histories, any()) }

            alluUpdateService.checkApplicationStatuses()

            verify {
                applicationRepository.getAllAlluIds()
                alluStatusRepository.getLastUpdateTime()
                cableReportService.getApplicationStatusHistories(alluids, eventsAfter)
                applicationService.handleApplicationUpdates(histories, any())
            }
        }

        @Test
        fun `calls application service with the current time`() {
            val alluids = listOf(23, 24)
            val lastUpdatedString = "2022-10-12T14:14:58.423521Z"
            val lastUpdated = OffsetDateTime.parse(lastUpdatedString)
            val eventsAfter = ZonedDateTime.parse(lastUpdatedString)
            every { applicationRepository.getAllAlluIds() } returns alluids
            every { alluStatusRepository.getLastUpdateTime() } returns lastUpdated
            every { cableReportService.getApplicationStatusHistories(alluids, eventsAfter) } returns
                listOf()
            justRun { applicationService.handleApplicationUpdates(listOf(), any()) }

            alluUpdateService.checkApplicationStatuses()

            verify {
                applicationRepository.getAllAlluIds()
                alluStatusRepository.getLastUpdateTime()
                cableReportService.getApplicationStatusHistories(alluids, eventsAfter)
                applicationService.handleApplicationUpdates(
                    listOf(),
                    withArg { assertThat(it).isRecent() }
                )
            }
        }
    }
}
