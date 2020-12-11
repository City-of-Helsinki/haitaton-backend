package fi.hel.haitaton.hanke

import mu.KotlinLogging
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.web.filter.ForwardedHeaderFilter

@SpringBootApplication
class Application {

    @Bean
    fun forwardedHeaderFilter(): ForwardedHeaderFilter? {
        return ForwardedHeaderFilter()
    }
}

private val logger = KotlinLogging.logger { }

fun main(args: Array<String>) {
    // FIXME This is only for debugging in OpenShift where it does not seem to be able to connect to the database for some reason
    /*
    logger.info {
        buildString {
            append("Environment:\n")
            append(System.getenv().entries.joinToString("\n") { "${it.key} = ${it.value}" })
        }
    }
     */
    runApplication<Application>(*args)
}
