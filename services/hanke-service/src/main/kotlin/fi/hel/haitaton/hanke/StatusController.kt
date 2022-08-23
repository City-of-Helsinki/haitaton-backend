package fi.hel.haitaton.hanke

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/status")
class StatusController(@Autowired private val jdbcOperations: JdbcOperations) {

    companion object {
        const val QUERY = "SELECT COUNT(*) FROM tormays_central_business_area_polys LIMIT 1"
        const val SUCCESS = 1
        const val ERROR = 0
    }

    @GetMapping
    fun getStatus(): ResponseEntity<Void> =
        if (getCount() == SUCCESS) {
            ResponseEntity.ok().build()
        } else {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }

    private fun getCount() =
        try {
             with(jdbcOperations) {
                queryForObject(QUERY, Int::class.java) ?: ERROR
             }
        } catch (ignore: Throwable) {
            ERROR
        }

}
