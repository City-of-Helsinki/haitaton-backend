package fi.hel.haitaton.hanke.configuration

import fi.hel.haitaton.hanke.hakemus.AlluUpdateService
import io.mockk.mockk
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MockAlluUpdateService {
    @Bean fun alluUpdateService(): AlluUpdateService = mockk()
}
