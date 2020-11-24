package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.geometria.HankeGeometriatDao
import fi.hel.haitaton.hanke.geometria.HankeGeometriatDaoImpl
import fi.hel.haitaton.hanke.geometria.HankeGeometriatService
import fi.hel.haitaton.hanke.geometria.HankeGeometriatServiceImpl
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcOperations

@Configuration
@Profile("default")
class Configuration {

    @Bean
    fun hankeService(hankeRepository: HankeRepository): HankeService = HankeServiceImpl(hankeRepository)

    @Bean
    fun hankeGeometriatDao(jdbcOperations: JdbcOperations): HankeGeometriatDao = HankeGeometriatDaoImpl(jdbcOperations)

    @Bean
    fun hankeGeometriatService(repository: HankeRepository, hankeGeometriatDao: HankeGeometriatDao): HankeGeometriatService = HankeGeometriatServiceImpl(repository, hankeGeometriatDao)
}