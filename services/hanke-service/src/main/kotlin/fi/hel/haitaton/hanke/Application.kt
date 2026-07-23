package fi.hel.haitaton.hanke

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.web.filter.ForwardedHeaderFilter
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@SpringBootApplication
@EnableCaching
class Application {

    @Value("\${haitaton.cors.allowedOrigins}") lateinit var allowedOrigins: String

    @Bean
    fun forwardedHeaderFilter(): ForwardedHeaderFilter? {
        return ForwardedHeaderFilter()
    }

    @Bean
    fun corsConfigurer(): WebMvcConfigurer? {
        return object : WebMvcConfigurer {
            override fun addCorsMappings(registry: CorsRegistry) {
                registry
                    .addMapping("/public-hankkeet")
                    .allowedMethods("GET")
                    .allowedOrigins(allowedOrigins)
                registry
                    .addMapping("/public-hankkeet/grid")
                    .allowedMethods("POST")
                    .allowedOrigins(allowedOrigins)
                registry
                    .addMapping("/hankkeet")
                    .allowedMethods("POST", "GET")
                    .allowedOrigins(allowedOrigins)
                registry
                    .addMapping("/hankkeet/**")
                    .allowedMethods("POST", "GET", "PUT", "DELETE")
                    .allowedOrigins(allowedOrigins)
                registry
                    .addMapping("/hakemukset/**")
                    .allowedMethods("POST", "GET", "PUT", "DELETE")
                    .allowedOrigins(allowedOrigins)
                registry
                    .addMapping("/hakemukset")
                    .allowedMethods("POST", "GET")
                    .allowedOrigins(allowedOrigins)
                registry
                    .addMapping("/organisaatiot")
                    .allowedMethods("GET")
                    .allowedOrigins(allowedOrigins)
            }
        }
    }
}

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
