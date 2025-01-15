package fi.hel.haitaton.hanke.pdf

import java.net.URI
import java.net.URL
import org.geotools.http.HTTPClientFinder
import org.geotools.http.LoggingHTTPClient
import org.geotools.ows.wms.WebMapServer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class WmsConfiguration(
    @Value("\${haitaton.map-service.capability-url}") private val mapServiceUrl: String
) {

    val url: URL = URI(mapServiceUrl).toURL()

    @Bean
    fun wms(): WebMapServer {
        val httpClient = LoggingHTTPClient(HTTPClientFinder.createClient())
        httpClient.isTryGzip = true
        return WebMapServer(url, httpClient)
    }
}
