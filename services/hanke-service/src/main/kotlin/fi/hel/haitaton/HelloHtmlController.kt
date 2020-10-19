package fi.hel.haitaton

import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ResponseBody
import java.util.concurrent.atomic.AtomicLong

/**
 * A simple useless starter web service returning a simple HTML page.
 *
 * Can be used to test development and build setups, and as a crude example HTML page service.
 * Has no security, and is not intended to have such, in order to emphasize easier testing
 * of the basic parts of development.
 *
 * If testing security at this kind simple of level is wanted, create another service class.
 */
@Controller
class HelloHtmlController {

    val counter = AtomicLong()

    @GetMapping("/", MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    fun hello() : String {
        return if (counter.incrementAndGet() < 2L)
            "<html>\n<header><title>Hello</title></header>\n<body>\nHello alive (counter $counter)\n</body>\n</html>"
        else
            "<html>\n<header><title>Hello</title></header>\n<body>\nHello still alive (counter $counter)\n</body>\n</html>"
    }

}
