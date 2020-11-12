package fi.hel.haitaton.hanke

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

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
