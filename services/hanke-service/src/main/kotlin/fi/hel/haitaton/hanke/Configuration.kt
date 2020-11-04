package fi.hel.haitaton.hanke

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class Configuration {

    @Bean
    fun hankeDao(): HankeDao {
        return HankeDaoImpl()
    }

    @Bean
    fun hankeService(dao: HankeDao): HankeService {
        return HankeServiceImpl(dao)
    }
}