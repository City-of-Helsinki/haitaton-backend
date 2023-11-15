package fi.hel.haitaton.hanke.configuration

import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.HankealueService
import fi.hel.haitaton.hanke.allu.AlluProperties
import fi.hel.haitaton.hanke.allu.AlluStatusRepository
import fi.hel.haitaton.hanke.allu.CableReportService
import fi.hel.haitaton.hanke.allu.CableReportServiceAllu
import fi.hel.haitaton.hanke.application.ApplicationRepository
import fi.hel.haitaton.hanke.application.ApplicationService
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentService
import fi.hel.haitaton.hanke.attachment.azure.Containers
import fi.hel.haitaton.hanke.email.EmailProperties
import fi.hel.haitaton.hanke.email.EmailSenderService
import fi.hel.haitaton.hanke.gdpr.GdprProperties
import fi.hel.haitaton.hanke.geometria.GeometriatDao
import fi.hel.haitaton.hanke.logging.ApplicationLoggingService
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.logging.HankeLoggingService
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluLaskentaService
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTormaysService
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient

@Configuration
@EnableConfigurationProperties(
    GdprProperties::class,
    FeatureFlags::class,
    AlluProperties::class,
    EmailProperties::class,
    Containers::class,
)
class Configuration {
    @Value("\${haitaton.allu.insecure}") var alluTrustInsecure: Boolean = false
    @Autowired lateinit var alluProperties: AlluProperties

    @Bean fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Bean
    fun cableReportService(webClientBuilder: WebClient.Builder): CableReportService {
        val webClient =
            webClientWithLargeBuffer(
                if (alluTrustInsecure) createInsecureTrustingWebClient(webClientBuilder)
                else webClientBuilder
            )
        return CableReportServiceAllu(webClient, alluProperties)
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
    fun applicationService(
        applicationRepository: ApplicationRepository,
        alluStatusRepository: AlluStatusRepository,
        cableReportService: CableReportService,
        disclosureLogService: DisclosureLogService,
        applicationLoggingService: ApplicationLoggingService,
        hankeKayttajaService: HankeKayttajaService,
        emailSenderService: EmailSenderService,
        applicationAttachmentService: ApplicationAttachmentService,
        geometriatDao: GeometriatDao,
        permissionService: PermissionService,
        hankeRepository: HankeRepository,
        hankeLoggingService: HankeLoggingService,
        featureFlags: FeatureFlags,
        hankealueService: HankealueService,
    ): ApplicationService =
        ApplicationService(
            applicationRepository,
            alluStatusRepository,
            cableReportService,
            disclosureLogService,
            applicationLoggingService,
            hankeKayttajaService,
            emailSenderService,
            applicationAttachmentService,
            geometriatDao,
            permissionService,
            hankeRepository,
            hankeLoggingService,
            featureFlags,
            hankealueService,
        )

    @Bean
    fun tormaystarkasteluLaskentaService(
        tormaystarkasteluDao: TormaystarkasteluTormaysService
    ): TormaystarkasteluLaskentaService = TormaystarkasteluLaskentaService(tormaystarkasteluDao)

    companion object {
        /** Create a web client that can download large files in memory. */
        fun webClientWithLargeBuffer(webClientBuilder: WebClient.Builder): WebClient =
            webClientBuilder
                .codecs { codecs -> codecs.defaultCodecs().maxInMemorySize(100 * 1024 * 1024) }
                .build()
    }
}
