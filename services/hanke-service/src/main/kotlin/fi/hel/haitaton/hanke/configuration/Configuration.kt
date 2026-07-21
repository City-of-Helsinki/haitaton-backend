package fi.hel.haitaton.hanke.configuration

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
import org.geojson.LngLatAlt
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.WebClient
import reactor.netty.http.client.HttpClient
import tools.jackson.databind.module.SimpleModule

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
     * Registers the same GeoJSON LngLatAlt serializer/deserializer used for hypersistence-utils'
     * JSON columns (see HypersistenceJsonSerializer.kt) on the app-wide JsonMapper.Builder that
     * Boot auto-configures and that WebClient's codecs are built from. Without this, Jackson 3's
     * default bean introspection corrupts LngLatAlt's array shape the same way it did on the
     * JSON-column path before that fix — geojson-jackson's own serializer is Jackson-2-only and
     * isn't picked up.
     */
    @Bean
    fun geoJsonJsonMapperBuilderCustomizer() = JsonMapperBuilderCustomizer { builder ->
        builder.addModule(
            SimpleModule()
                .addSerializer(LngLatAlt::class.java, LngLatAltJackson3Serializer())
                .addDeserializer(LngLatAlt::class.java, LngLatAltJackson3Deserializer())
        )
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
