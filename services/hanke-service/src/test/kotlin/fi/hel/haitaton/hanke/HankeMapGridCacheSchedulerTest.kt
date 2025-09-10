package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.configuration.LockService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class HankeMapGridCacheSchedulerTest {

    private val hankeMapGridService = mockk<HankeMapGridService>(relaxed = true)
    private val lockService = mockk<LockService>()
    private val scheduler = HankeMapGridCacheScheduler(hankeMapGridService, lockService)

    @Test
    fun `repopulateCache calls service when lock is available`() {
        every { lockService.doIfUnlocked(any(), any()) } answers
            {
                secondArg<() -> Unit>().invoke()
            }

        scheduler.repopulateCache()

        verify { hankeMapGridService.repopulateCache() }
    }

    @Test
    fun `repopulateCache handles exceptions gracefully`() {
        every { hankeMapGridService.repopulateCache() } throws RuntimeException("Test error")
        every { lockService.doIfUnlocked(any(), any()) } answers
            {
                secondArg<() -> Unit>().invoke()
            }

        // Should not throw exception
        scheduler.repopulateCache()

        verify { hankeMapGridService.repopulateCache() }
    }

    @Test
    fun `repopulateCache does nothing when lock is not available`() {
        every { lockService.doIfUnlocked(any(), any()) } returns Unit

        scheduler.repopulateCache()

        verify(exactly = 0) { hankeMapGridService.repopulateCache() }
    }
}
