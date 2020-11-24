package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.geometria.HankeGeometriatDao
import fi.hel.haitaton.hanke.geometria.HankeGeometriatDaoImpl
import fi.hel.haitaton.hanke.geometria.HankeGeometriatService
import fi.hel.haitaton.hanke.geometria.HankeGeometriatServiceImpl
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class Configuration {

    @Bean
    fun hankeGeometriatDao(): HankeGeometriatDao {
        return HankeGeometriatDaoImpl()
    }

    @Bean
    fun hankeGeometriatService(repository: HankeRepository, hankeGeometriatDao: HankeGeometriatDao): HankeGeometriatService {
        return HankeGeometriatServiceImpl(repository, hankeGeometriatDao)
    }
}