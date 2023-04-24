package fi.hel.haitaton.hanke.configuration

import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.HankeServiceImpl
import fi.hel.haitaton.hanke.HanketunnusService
import fi.hel.haitaton.hanke.HanketunnusServiceImpl
import fi.hel.haitaton.hanke.IdCounterRepository
import fi.hel.haitaton.hanke.allu.AlluProperties
import fi.hel.haitaton.hanke.allu.AlluStatusRepository
import fi.hel.haitaton.hanke.allu.CableReportService
import fi.hel.haitaton.hanke.allu.CableReportServiceAllu
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.application.ApplicationService
import fi.hel.haitaton.hanke.attachment.AttachmentRepository
import fi.hel.haitaton.hanke.attachment.AttachmentService
import fi.hel.haitaton.hanke.attachment.AttachmentServiceImpl
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
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
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
import org.springframework.web.multipart.MultipartResolver
import org.springframework.web.multipart.commons.CommonsMultipartResolver
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
    fun cableReportService(webClientBuilder: WebClient.Builder): CableReportService {
        val webClient =
            webClientWithLargeBuffer(
                if (alluTrustInsecure) createInsecureTrustingWebClient(webClientBuilder)
                else webClientBuilder
            )
        val alluProps =
            AlluProperties(baseUrl = alluBaseUrl, username = alluUsername, password = alluPassword)
        return CableReportServiceAllu(webClient, alluProps)
    }

    private fun createInsecureTrustingWebClient(
        webClientBuilder: WebClient.Builder
    ): WebClient.Builder {
        val sslContext =
            SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build()
        val httpClient = HttpClient.create().secure { t -> t.sslContext(sslContext) }
        return webClientBuilder.clientConnector(ReactorClientHttpConnector(httpClient))
    }

    @Bean
    fun multipartResolver(): MultipartResolver {
        return CommonsMultipartResolver()
    }

    @Bean
    fun applicationService(
        applicationRepository: ApplicationRepository,
        alluStatusRepository: AlluStatusRepository,
        cableReportService: CableReportService,
        disclosureLogService: DisclosureLogService,
        applicationLoggingService: ApplicationLoggingService,
        hankeKayttajaService: HankeKayttajaService,
        geometriatDao: GeometriatDao,
        permissionService: PermissionService,
        hankeRepository: HankeRepository,
    ): ApplicationService =
        ApplicationService(
            applicationRepository,
            alluStatusRepository,
            cableReportService,
            disclosureLogService,
            applicationLoggingService,
            hankeKayttajaService,
            geometriatDao,
            permissionService,
            hankeRepository,
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
        hankeLoggingService: HankeLoggingService,
        applicationService: ApplicationService,
        permissionService: PermissionService,
        hankeKayttajaService: HankeKayttajaService,
    ): HankeService =
        HankeServiceImpl(
            hankeRepository,
            tormaystarkasteluLaskentaService,
            hanketunnusService,
            geometriatService,
            auditLogService,
            hankeLoggingService,
            applicationService,
            permissionService,
            hankeKayttajaService,
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

    @Bean
    fun attachmentsService(
        hankeRepository: HankeRepository,
        attachmentRepository: AttachmentRepository
    ): AttachmentService = AttachmentServiceImpl(hankeRepository, attachmentRepository)

    companion object {
        /** Create a web client that can download large files in memory. Up to 20 megabytes. */
        fun webClientWithLargeBuffer(webClientBuilder: WebClient.Builder): WebClient =
            webClientBuilder
                .codecs { codecs -> codecs.defaultCodecs().maxInMemorySize(20 * 1024 * 1024) }
                .build()
    }
}
