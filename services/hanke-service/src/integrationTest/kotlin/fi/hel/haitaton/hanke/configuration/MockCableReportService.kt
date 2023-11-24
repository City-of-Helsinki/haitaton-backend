package fi.hel.haitaton.hanke.configuration

import fi.hel.haitaton.hanke.allu.CableReportService
import io.mockk.mockk
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MockCableReportService {
    @Bean fun cableReportService(): CableReportService = mockk()
}
