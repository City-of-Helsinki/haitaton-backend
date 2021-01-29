package fi.hel.haitaton.hanke

import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.web.filter.ForwardedHeaderFilter
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@SpringBootApplication
class Application {

    @Value("\${haitaton.cors.allowedOrigins}")
    lateinit var allowedOrigins: String

    @Bean
    fun forwardedHeaderFilter(): ForwardedHeaderFilter? {
        return ForwardedHeaderFilter()
    }

    @Bean
    fun corsConfigurer(): WebMvcConfigurer? {
        return object : WebMvcConfigurer {
            override fun addCorsMappings(registry: CorsRegistry) {
                registry.addMapping("/hankkeet").allowedMethods("POST", "GET").allowedOrigins(allowedOrigins)
                registry.addMapping("/hankkeet/**").allowedMethods("POST", "GET", "PUT").allowedOrigins(allowedOrigins)
                registry.addMapping("/organisaatiot").allowedMethods("GET").allowedOrigins(allowedOrigins)
            }
        }
    }
}

private val logger = KotlinLogging.logger { }

fun main(args: Array<String>) {
    // FIXME This is only for debugging in OpenShift where it does not seem to be able to connect to the database for some reason
    logger.info {
        buildString {
            append("Environment:\n")
            append(System.getenv().entries.joinToString("\n") { "${it.key} = ${it.value}" })
        }
    }
    runApplication<Application>(*args)
}
