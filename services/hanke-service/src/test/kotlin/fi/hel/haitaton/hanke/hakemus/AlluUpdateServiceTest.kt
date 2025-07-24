package fi.hel.haitaton.hanke.hakemus

import assertk.assertThat
import assertk.assertions.contains
import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory.asList
import fi.hel.haitaton.hanke.factory.ApplicationHistoryFactory.withDefaultEvents
import io.mockk.Called
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension

@ExtendWith(OutputCaptureExtension::class)
class AlluUpdateServiceTest {
    private val alluClient: AlluClient = mockk()
    private val historyService: HakemusHistoryService = mockk()

    private val alluUpdateService = AlluUpdateService(alluClient, historyService)

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun confirmMocks() {
        checkUnnecessaryStub()
        confirmVerified(historyService, alluClient)
    }

    @Test
    fun `does nothing when no alluIds`(output: CapturedOutput) {
        every { historyService.getAllAlluIds() } returns listOf()

        alluUpdateService.handleUpdates()

        assertThat(output)
            .contains("There are no applications to update, skipping Allu history update.")
        verifySequence {
            historyService.getAllAlluIds()
            alluClient wasNot Called
        }
    }

    @Test
    fun `handles updates when no histories`() {
        every { historyService.getAllAlluIds() } returns listOf(23)
        every { historyService.getLastUpdateTime() } returns OffsetDateTime.now()
        every { alluClient.getApplicationStatusHistories(any(), any()) } returns emptyList()
        justRun { historyService.setLastUpdateTime(any()) }
        justRun { historyService.processApplicationHistories(emptyList()) }

        alluUpdateService.handleUpdates()

        verifySequence {
            historyService.getAllAlluIds()
            historyService.getLastUpdateTime()
            alluClient.getApplicationStatusHistories(any(), any())
            historyService.setLastUpdateTime(any())
            historyService.processApplicationHistories(emptyList())
        }
    }

    @Test
    fun `handles updates when histories`() {
        val histories =
            ApplicationHistoryFactory.create(applicationId = 24, *emptyArray())
                .withDefaultEvents()
                .asList()
        every { historyService.getAllAlluIds() } returns listOf(23)
        every { historyService.getLastUpdateTime() } returns OffsetDateTime.now()
        every { alluClient.getApplicationStatusHistories(any(), any()) } returns histories
        justRun { historyService.setLastUpdateTime(any()) }
        justRun { historyService.processApplicationHistories(histories) }

        alluUpdateService.handleUpdates()

        verifySequence {
            historyService.getAllAlluIds()
            historyService.getLastUpdateTime()
            alluClient.getApplicationStatusHistories(any(), any())
            historyService.setLastUpdateTime(any())
            historyService.processApplicationHistories(histories)
        }
    }
}
