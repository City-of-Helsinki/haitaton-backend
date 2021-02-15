package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.geometria.HankeGeometriatDao
import fi.hel.haitaton.hanke.geometria.HankeGeometriatService
import fi.hel.haitaton.hanke.organisaatio.OrganisaatioService
import io.mockk.mockk
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcOperations

@Configuration
@Profile("itest")
class IntegrationTestConfiguration {

    @Bean
    fun jdbcOperations(): JdbcOperations = mockk()

    @Bean
    fun hankeRepository(): HankeRepository = mockk()

    @Bean
    fun hanketunnusService(): HanketunnusService = mockk()

    @Bean
    fun hankeService(): HankeService = mockk()

    @Bean
    fun organisaatioService(): OrganisaatioService = mockk()

    @Bean
    fun hankeGeometriatDao(jdbcOperations: JdbcOperations): HankeGeometriatDao = mockk()

    @Bean
    fun hankeGeometriatService(service: HankeService, hankeGeometriatDao: HankeGeometriatDao): HankeGeometriatService = mockk()
}