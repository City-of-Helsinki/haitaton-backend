package fi.hel.haitaton

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.atomic.AtomicLong

/**
 * A simple useless starter REST API service.
 *
 * Can be used to test development and build setups, and as a crude example REST service.
 * Has no security, and is not intended to have such, in order to emphasize easier testing
 * of the basic parts of development.
 *
 * If testing security at this kind simple of level is wanted, create another service class.
 */
@RestController
@RequestMapping("/api/hello")
class HelloApiController {

    val apiCallCounter = AtomicLong()

    @GetMapping("/")
    fun hello() : ResponseEntity<Any> {
        return if (apiCallCounter.incrementAndGet() < 2L)
            ResponseEntity.ok(HelloResponse(apiCallCounter.get(), "Hello, world!"))
        else
            ResponseEntity.ok(HelloResponse(apiCallCounter.get(), "Hi again, world!"))
    }

}