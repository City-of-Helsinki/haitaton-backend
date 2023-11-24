package fi.hel.haitaton.hanke.configuration

import fi.hel.haitaton.hanke.allu.AlluProperties
import fi.hel.haitaton.hanke.allu.CableReportService
import fi.hel.haitaton.hanke.attachment.azure.Containers
import fi.hel.haitaton.hanke.email.EmailProperties
import fi.hel.haitaton.hanke.gdpr.GdprProperties
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
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
    @Profile("!test")
    fun cableReportService(webClientBuilder: WebClient.Builder): CableReportService {
        val webClient =
            webClientWithLargeBuffer(
                if (alluTrustInsecure) createInsecureTrustingWebClient(webClientBuilder)
                else webClientBuilder
            )
        return CableReportService(webClient, alluProperties)
    }

    private fun createInsecureTrustingWebClient(
        webClientBuilder: WebClient.Builder
    ): WebClient.Builder {
        val sslContext =
            SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build()
        val httpClient = HttpClient.create().secure { t -> t.sslContext(sslContext) }
        return webClientBuilder.clientConnector(ReactorClientHttpConnector(httpClient))
    }

    companion object {
        /** Create a web client that can download large files in memory. */
        fun webClientWithLargeBuffer(webClientBuilder: WebClient.Builder): WebClient =
            webClientBuilder
                .codecs { codecs -> codecs.defaultCodecs().maxInMemorySize(100 * 1024 * 1024) }
                .build()
    }
}
