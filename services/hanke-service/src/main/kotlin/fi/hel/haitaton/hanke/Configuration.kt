package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.allu.AlluProperties
import fi.hel.haitaton.hanke.allu.ApplicationRepository
import fi.hel.haitaton.hanke.allu.ApplicationService
import fi.hel.haitaton.hanke.allu.CableReportServiceAllu
import fi.hel.haitaton.hanke.geometria.GeometriatDao
import fi.hel.haitaton.hanke.geometria.GeometriatDaoImpl
import fi.hel.haitaton.hanke.geometria.GeometriatService
import fi.hel.haitaton.hanke.geometria.GeometriatServiceImpl
import fi.hel.haitaton.hanke.logging.ApplicationLoggingService
import fi.hel.haitaton.hanke.logging.AuditLogService
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.logging.HankeLoggingService
import fi.hel.haitaton.hanke.organisaatio.OrganisaatioRepository
import fi.hel.haitaton.hanke.organisaatio.OrganisaatioService
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.tormaystarkastelu.LuokitteluRajaArvotService
import fi.hel.haitaton.hanke.tormaystarkastelu.LuokitteluRajaArvotServiceHardCoded
import fi.hel.haitaton.hanke.tormaystarkastelu.PerusIndeksiPainotService
import fi.hel.haitaton.hanke.tormaystarkastelu.PerusIndeksiPainotServiceHardCoded
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluLaskentaService
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTormaysService
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTormaysServicePG
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

    @Value("\${haitaton.allu.baseUrl}") lateinit var alluBaseUrl: String
    @Value("\${haitaton.allu.username}") lateinit var alluUsername: String
    @Value("\${haitaton.allu.password}") lateinit var alluPassword: String
    @Value("\${haitaton.allu.insecure}") var alluTrustInsecure: Boolean = false

    @Bean
    fun cableReportServiceAllu(): CableReportServiceAllu {
        val webClient =
            if (alluTrustInsecure) createInsecureTrustingWebClient() else WebClient.create()
        val alluProps =
            AlluProperties(baseUrl = alluBaseUrl, username = alluUsername, password = alluPassword)
        return CableReportServiceAllu(webClient, alluProps)
    }

    private fun createInsecureTrustingWebClient(): WebClient {
        val sslContext =
            SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build()
        val httpClient = HttpClient.create().secure { t -> t.sslContext(sslContext) }
        return WebClient.builder().clientConnector(ReactorClientHttpConnector(httpClient)).build()
    }

    @Bean
    fun applicationService(
        applicationRepository: ApplicationRepository,
        cableReportServiceAllu: CableReportServiceAllu,
        disclosureLogService: DisclosureLogService,
        applicationLoggingService: ApplicationLoggingService,
    ): ApplicationService =
        ApplicationService(
            applicationRepository,
            cableReportServiceAllu,
            disclosureLogService,
            applicationLoggingService,
        )

    @Bean
    fun hanketunnusService(idCounterRepository: IdCounterRepository): HanketunnusService =
        HanketunnusServiceImpl(idCounterRepository)

    @Bean
    fun hankeService(
        hankeRepository: HankeRepository,
        tormaystarkasteluLaskentaService: TormaystarkasteluLaskentaService,
        geometriatService: GeometriatService,
        hanketunnusService: HanketunnusService,
        auditLogService: AuditLogService,
        permissionService: PermissionService,
        hankeLoggingService: HankeLoggingService,
    ): HankeService =
        HankeServiceImpl(
            hankeRepository,
            tormaystarkasteluLaskentaService,
            hanketunnusService,
            geometriatService,
            auditLogService,
            hankeLoggingService,
        )

    @Bean
    fun organisaatioService(organisaatioRepository: OrganisaatioRepository): OrganisaatioService =
        OrganisaatioService(organisaatioRepository)

    @Bean
    fun geometriatDao(jdbcOperations: JdbcOperations): GeometriatDao =
        GeometriatDaoImpl(jdbcOperations)

    @Bean
    fun geometriatService(geometriatDao: GeometriatDao): GeometriatService =
        GeometriatServiceImpl(geometriatDao)

    @Bean
    fun tormaysService(jdbcOperations: JdbcOperations): TormaystarkasteluTormaysService =
        TormaystarkasteluTormaysServicePG(jdbcOperations)

    @Bean
    fun perusIndeksiPainotService(): PerusIndeksiPainotService =
        PerusIndeksiPainotServiceHardCoded()

    @Bean
    fun luokitteluRajaArvotService(): LuokitteluRajaArvotService =
        LuokitteluRajaArvotServiceHardCoded()

    @Bean
    fun tormaystarkasteluLaskentaService(
        luokitteluRajaArvotService: LuokitteluRajaArvotService,
        perusIndeksiPainotService: PerusIndeksiPainotService,
        tormaystarkasteluDao: TormaystarkasteluTormaysService
    ): TormaystarkasteluLaskentaService =
        TormaystarkasteluLaskentaService(
            luokitteluRajaArvotService,
            perusIndeksiPainotService,
            tormaystarkasteluDao
        )
}
