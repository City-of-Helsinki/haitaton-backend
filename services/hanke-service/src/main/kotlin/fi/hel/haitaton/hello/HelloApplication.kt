package fi.hel.haitaton.hello

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan

@SpringBootApplication
@ComponentScan("fi.hel.haitaton")
class HelloApplication

fun main(args: Array<String>) {
	runApplication<HelloApplication>(*args)
}
