package fi.hel.haitaton.hanke.testdata

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/testdata")
@ConditionalOnProperty(name = ["haitaton.testdata.enabled"], havingValue = "true")
class TestDataController(private val testDataService: TestDataService) {

    @EventListener(ApplicationReadyEvent::class)
    fun logWarning() {
        logger.warn { "Test data endpoint enabled." }
    }

    @Operation(
        summary = "Unlink applications from allu",
        description =
            "Removes the Allu IDs and statuses from all applications, effectively " +
                "unlinking them from Allu and returning them to drafts. The applications " +
                "can be re-sent to allu whenever. This endpoint can be used to unlink all " +
                "applications from Allu after Allu wipes all of it's data during test " +
                "environment update. Allu will re-issue the same IDs, causing collisions " +
                "in Haitaton.",
    )
    @PostMapping("/unlink-applications")
    fun unlinkApplicationsFromAllu() {
        testDataService.unlinkApplicationsFromAllu()
        logger.warn { "Unlinked all applications from Allu." }
    }

    @Operation(summary = "Trigger Allu updates")
    @GetMapping("/trigger-allu")
    @ResponseBody
    fun triggerAllu(): ResponseEntity<String> {
        testDataService.triggerAlluUpdates()
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body("Allu updates done.")
    }

    @Operation(
        summary = "Create random public hanke",
        description =
            "Creates a specified number of random public hanke with geometries for testing map performance",
    )
    @PostMapping("/create-public-hanke/{count}")
    @ResponseBody
    fun createPublicHanke(@PathVariable count: Int): ResponseEntity<String> {
        if (count !in 1..1000) {
            return ResponseEntity.badRequest()
                .contentType(MediaType.TEXT_PLAIN)
                .body("Count must be between 1 and 1000")
        }

        val created = testDataService.createRandomPublicHanke(count)

        logger.warn { "Created $created random public hanke" }

        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .body("Created $created random public hanke")
    }

    @Operation(
        summary = "Terminate current user session",
        description =
            "Deletes the current user's session from the database to simulate backchannel logout. " +
                "The next authenticated request will receive HAI0006 error. " +
                "This endpoint requires authentication and is only available when testdata is enabled.",
    )
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/terminate-session")
    @ResponseBody
    fun terminateCurrentSession(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<String> {
        val sessionId =
            jwt.getClaim<String>("sid")
                ?: return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body("No session ID found in JWT token")

        val deleted = testDataService.terminateUserSession(sessionId)

        return ResponseEntity.ok()
            .contentType(MediaType.TEXT_PLAIN)
            .body("Session terminated (deleted=$deleted). Next request will receive HAI0006 error.")
    }
}
