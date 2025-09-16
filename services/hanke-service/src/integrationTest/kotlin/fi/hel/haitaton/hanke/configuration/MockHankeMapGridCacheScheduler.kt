package fi.hel.haitaton.hanke.configuration

import fi.hel.haitaton.hanke.HankeMapGridCacheScheduler
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class MockHankeMapGridCacheSchedulerConfiguration {

    @Bean
    @Primary
    fun mockHankeMapGridCacheScheduler(): HankeMapGridCacheScheduler {
        // Create a mock that does nothing in tests
        return mockk<HankeMapGridCacheScheduler>(relaxed = true)
    }
}
