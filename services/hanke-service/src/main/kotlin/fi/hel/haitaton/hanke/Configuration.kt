package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.allu.AlluProperties
import fi.hel.haitaton.hanke.allu.ApplicationRepository
import fi.hel.haitaton.hanke.allu.ApplicationService
import fi.hel.haitaton.hanke.allu.CableReportServiceAllu
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
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient

@Configuration
@Profile("default")
class Configuration {

    @Value("\${haitaton.allu.baseUrl}")
    lateinit var alluBaseUrl : String
    @Value("\${haitaton.allu.username}")
    lateinit var alluUsername : String
    @Value("\${haitaton.allu.password}")
    lateinit var alluPassword : String
    @Value("\${haitaton.allu.insecure}")
    var alluTrustInsecure: Boolean = false

    @Bean
    fun cableReportServiceAllu(): CableReportServiceAllu {
        val webClient = if (alluTrustInsecure) createInsecureTrustingWebClient() else WebClient.create()
        val alluProps = AlluProperties(
                baseUrl = alluBaseUrl,
                username = alluUsername,
                password = alluPassword
        )
        return CableReportServiceAllu(webClient, alluProps)
    }

    private fun createInsecureTrustingWebClient(): WebClient {
        val sslContext = SslContextBuilder
                .forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build()
        val httpClient = HttpClient.create().secure { t -> t.sslContext(sslContext) }
        return WebClient.builder().clientConnector(ReactorClientHttpConnector(httpClient)).build()
    }

    @Bean
    fun applicationService(applicationRepository: ApplicationRepository, cableReportServiceAllu: CableReportServiceAllu) : ApplicationService = ApplicationService(applicationRepository, cableReportServiceAllu)


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
