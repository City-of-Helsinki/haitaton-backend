package fi.hel.haitaton.hanke.logging

import ch.qos.logback.access.tomcat.LogbackValve

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory
import org.springframework.boot.web.server.WebServerFactoryCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class AccessLogConfiguration {

    @Bean
    fun accessLogsCustomizer() = WebServerFactoryCustomizer<TomcatServletWebServerFactory> { factory ->
        LogbackValve().let {
            it.filename = "logback-access.xml"
            it.isAsyncSupported = true
            factory.addContextValves(it)
        }
    }

}
