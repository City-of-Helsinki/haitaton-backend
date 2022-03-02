package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.application.ApplicationService
import fi.hel.haitaton.hanke.geometria.HankeGeometriatDao
import fi.hel.haitaton.hanke.geometria.HankeGeometriatDaoImpl
import fi.hel.haitaton.hanke.geometria.HankeGeometriatService
import fi.hel.haitaton.hanke.geometria.HankeGeometriatServiceImpl
import fi.hel.haitaton.hanke.logging.PersonalDataAuditLogRepository
import fi.hel.haitaton.hanke.logging.PersonalDataChangeLogRepository
import fi.hel.haitaton.hanke.organisaatio.OrganisaatioRepository
import fi.hel.haitaton.hanke.organisaatio.OrganisaatioService
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.tormaystarkastelu.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcOperations

@Configuration
@Profile("default")
class Configuration {

    @Bean
    fun applicationService(applicationRepository: ApplicationRepository) : ApplicationService = ApplicationService(applicationRepository)

    @Bean
    fun hanketunnusService(idCounterRepository: IdCounterRepository): HanketunnusService =
        HanketunnusServiceImpl(idCounterRepository)

    @Bean
    fun hankeService(
        hankeRepository: HankeRepository,
        tormaystarkasteluLaskentaService: TormaystarkasteluLaskentaService,
        hanketunnusService: HanketunnusService,
        personalDataAuditLogRepository: PersonalDataAuditLogRepository,
        personalDataChangeLogRepository: PersonalDataChangeLogRepository,
        permissionService: PermissionService
    ): HankeService =
        HankeServiceImpl(hankeRepository, tormaystarkasteluLaskentaService,
                hanketunnusService, personalDataAuditLogRepository, personalDataChangeLogRepository)

    @Bean
    fun organisaatioService(organisaatioRepository: OrganisaatioRepository): OrganisaatioService =
        OrganisaatioService(organisaatioRepository)

    @Bean
    fun hankeGeometriatDao(jdbcOperations: JdbcOperations): HankeGeometriatDao = HankeGeometriatDaoImpl(jdbcOperations)

    @Bean
    fun hankeGeometriatService(
        hankeGeometriatDao: HankeGeometriatDao
    ): HankeGeometriatService = HankeGeometriatServiceImpl(hankeGeometriatDao)

    @Bean
    fun tormaysService(jdbcOperations: JdbcOperations): TormaystarkasteluTormaysService =
        TormaystarkasteluTormaysServicePG(jdbcOperations)

    @Bean
    fun perusIndeksiPainotService(): PerusIndeksiPainotService = PerusIndeksiPainotServiceHardCoded()

    @Bean
    fun luokitteluRajaArvotService(): LuokitteluRajaArvotService = LuokitteluRajaArvotServiceHardCoded()

    @Bean
    fun tormaystarkasteluLaskentaService(
            luokitteluRajaArvotService: LuokitteluRajaArvotService,
            perusIndeksiPainotService: PerusIndeksiPainotService,
            tormaystarkasteluDao: TormaystarkasteluTormaysService): TormaystarkasteluLaskentaService =
            TormaystarkasteluLaskentaService(luokitteluRajaArvotService, perusIndeksiPainotService, tormaystarkasteluDao)
}
