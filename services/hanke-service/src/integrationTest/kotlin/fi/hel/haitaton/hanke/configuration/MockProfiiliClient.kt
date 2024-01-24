package fi.hel.haitaton.hanke.configuration

import fi.hel.haitaton.hanke.profiili.ProfiiliClient
import io.mockk.mockk
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MockProfiiliClient {
    @Bean fun profiiliClient(): ProfiiliClient = mockk()
}
