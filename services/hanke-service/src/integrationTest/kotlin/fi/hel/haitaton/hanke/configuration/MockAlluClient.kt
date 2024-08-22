package fi.hel.haitaton.hanke.configuration

import fi.hel.haitaton.hanke.allu.AlluClient
import io.mockk.mockk
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MockAlluClient {
    @Bean fun alluClient(): AlluClient = mockk()
}
