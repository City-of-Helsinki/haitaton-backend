@file:Suppress("DEPRECATION")

package fi.hel.haitaton.hanke.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import fi.hel.haitaton.hanke.allu.AlluClient
import fi.hel.haitaton.hanke.allu.AlluProperties
import fi.hel.haitaton.hanke.attachment.azure.Containers
import fi.hel.haitaton.hanke.email.EmailProperties
import fi.hel.haitaton.hanke.gdpr.GdprProperties
import fi.hel.haitaton.hanke.profiili.ProfiiliProperties
import fi.hel.haitaton.hanke.security.AdFilterProperties
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.webclient.WebClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.codec.json.Jackson2JsonEncoder
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient

@Configuration
@EnableConfigurationProperties(
    GdprProperties::class,
    FeatureFlags::class,
    AlluProperties::class,
    ProfiiliProperties::class,
    EmailProperties::class,
    AdFilterProperties::class,
    Containers::class,
)
class Configuration {
    @Value("\${haitaton.allu.insecure}") var alluTrustInsecure: Boolean = false
    @Autowired lateinit var alluProperties: AlluProperties

    @Bean fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    /**
     * Spring Boot 4's auto-configured WebClient.Builder defaults to Jackson 3 codecs. Keep it on
     * Jackson 2 for now, matching spring.jackson.use-jackson2-defaults, since WebClient consumers
     * such as ProfiiliClient still decode into com.fasterxml.jackson.databind.JsonNode.
     */
    @Bean
    fun jackson2WebClientCustomizer(objectMapper: ObjectMapper) = WebClientCustomizer { builder ->
        builder.codecs {
            it.defaultCodecs().jackson2JsonEncoder(Jackson2JsonEncoder(objectMapper))
            it.defaultCodecs().jackson2JsonDecoder(Jackson2JsonDecoder(objectMapper))
        }
    }

    @Bean
    @Profile("!test")
    fun alluClient(webClientBuilder: WebClient.Builder): AlluClient {
        val webClient =
            webClientWithLargeBuffer(
                if (alluTrustInsecure) createInsecureTrustingWebClient(webClientBuilder)
                else webClientBuilder
            )
        return AlluClient(webClient, alluProperties)
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
