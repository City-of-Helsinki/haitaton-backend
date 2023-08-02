package fi.hel.haitaton.hanke

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/status")
class StatusController(@Autowired private val jdbcOperations: JdbcOperations) {

    companion object {
        const val QUERY = "SELECT EXISTS(SELECT 1 FROM tormays_central_business_area_polys LIMIT 1)"
    }

    @GetMapping
    @Operation(
        summary = "Check application status",
        description = "Health check to verify application status."
    )
    @ApiResponses(
        value =
            [
                ApiResponse(description = "Success", responseCode = "200"),
                ApiResponse(description = "Internal server error", responseCode = "500")
            ]
    )
    fun getStatus(): ResponseEntity<Void> {
        return if (canConnectToDatabase()) {
            ResponseEntity.ok().build()
        } else {
            logger.error { "Central business area polys not found." }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
        }
    }

    private fun canConnectToDatabase() =
        try {
            with(jdbcOperations) { queryForObject(QUERY, Boolean::class.java) ?: false }
        } catch (e: Throwable) {
            logger.error(e) { "Error while checking database connection for status endpoint." }
            false
        }
}
