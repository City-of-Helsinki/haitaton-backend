package fi.hel.haitaton.hanke.configuration

import fi.hel.haitaton.hanke.hakemus.AlluUpdateScheduler
import io.mockk.mockk
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class MockAlluUpdateScheduler {
    @Bean fun alluUpdateScheduler(): AlluUpdateScheduler = mockk()
}
