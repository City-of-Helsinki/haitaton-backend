package fi.hel.haitaton.hello

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class HelloApplication

fun main(args: Array<String>) {
	runApplication<HelloApplication>(*args)
}
