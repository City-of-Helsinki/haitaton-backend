package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.geometria.HankeGeometriatDao
import fi.hel.haitaton.hanke.geometria.HankeGeometriatDaoImpl
import fi.hel.haitaton.hanke.geometria.HankeGeometriatService
import fi.hel.haitaton.hanke.geometria.HankeGeometriatServiceImpl
import fi.hel.haitaton.hanke.organisaatio.OrganisaatioRepository
import fi.hel.haitaton.hanke.organisaatio.OrganisaatioService
import fi.hel.haitaton.hanke.organisaatio.OrganisaatioServiceImpl
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluDao
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluDaoImpl
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluLaskentaService
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluLaskentaServiceImpl
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluLuokitteluService
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluLuokitteluServiceImpl
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcOperations

@Configuration
@Profile("default")
class Configuration {

    @Bean
    fun hanketunnusService(idCounterRepository: IdCounterRepository): HanketunnusService =
        HanketunnusServiceImpl(idCounterRepository)

    @Bean
    fun hankeService(hankeRepository: HankeRepository, hanketunnusService: HanketunnusService): HankeService =
        HankeServiceImpl(hankeRepository, hanketunnusService)

    @Bean
    fun organisaatioService(organisaatioRepository: OrganisaatioRepository): OrganisaatioService =
        OrganisaatioServiceImpl(organisaatioRepository)

    @Bean
    fun hankeGeometriatDao(jdbcOperations: JdbcOperations): HankeGeometriatDao = HankeGeometriatDaoImpl(jdbcOperations)

    @Bean
    fun hankeGeometriatService(
        hankeService: HankeService,
        hankeGeometriatDao: HankeGeometriatDao
    ): HankeGeometriatService = HankeGeometriatServiceImpl(hankeService, hankeGeometriatDao)

    @Bean
    fun tormaystarkasteluDao(jdbcOperations: JdbcOperations): TormaystarkasteluDao =
        TormaystarkasteluDaoImpl(jdbcOperations)

    @Bean
    fun tormaystarkasteluPaikkaService(
        tormaystarkasteluDao: TormaystarkasteluDao
    ): TormaystarkasteluLuokitteluService = TormaystarkasteluLuokitteluServiceImpl(tormaystarkasteluDao)

    @Bean
    fun tormaystarkasteluLaskentaService(
        hankeService: HankeService,
        luokitteluService: TormaystarkasteluLuokitteluService,
        geometriatService: HankeGeometriatService
    ): TormaystarkasteluLaskentaService =
        TormaystarkasteluLaskentaServiceImpl(hankeService, luokitteluService, geometriatService)
}
