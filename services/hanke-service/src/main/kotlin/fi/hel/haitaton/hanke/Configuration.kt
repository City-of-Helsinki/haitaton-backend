package fi.hel.haitaton.hanke

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class Configuration {

    @Bean
    fun hankeGeometriaDao(): HankeDao {
        return HankeDaoImpl()
    }

    @Bean
    fun hankeService(dao: HankeDao): HankeService {
        return HankeServiceImpl(dao)
    }

    @Bean
    fun hankeGeometriaService(dao: HankeDao): HankeGeometriaService {
        return HankeGeometriaServiceImpl(dao)
    }
}