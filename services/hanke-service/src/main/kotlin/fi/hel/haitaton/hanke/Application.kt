package fi.hel.haitaton.hanke

import mu.KotlinLogging
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.web.filter.ForwardedHeaderFilter
import org.springframework.web.servlet.config.annotation.CorsRegistry

import org.springframework.web.servlet.config.annotation.WebMvcConfigurer




@SpringBootApplication
class Application {

    @Bean
    fun forwardedHeaderFilter(): ForwardedHeaderFilter? {
        return ForwardedHeaderFilter()
    }

    @Bean
    fun corsConfigurer(): WebMvcConfigurer? {
        return object : WebMvcConfigurer {
            override fun addCorsMappings(registry: CorsRegistry) {
                registry.addMapping("/hankkeet").allowedOrigins("http://localhost", "http://localhost:8000", "http://localhost:3001", "https://haitaton-ui-dev.agw.arodevtest.hel.fi")
                registry.addMapping("/organisaatiot").allowedOrigins("http://localhost", "http://localhost:8000", "http://localhost:3001", "https://haitaton-ui-dev.agw.arodevtest.hel.fi")
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
