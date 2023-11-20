package fi.hel.haitaton.hanke.testdata

import fi.hel.haitaton.hanke.attachment.hanke.HankeAttachmentService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import java.util.UUID
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/testdata")
@ConditionalOnProperty(name = ["haitaton.testdata.enabled"], havingValue = "true")
class TestDataController(
    private val testDataService: TestDataService,
    private val hankeAttachmentService: HankeAttachmentService,
) {

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
                "in Haitaton."
    )
    @PostMapping("/unlink-applications")
    fun unlinkApplicationsFromAllu() {
        testDataService.unlinkApplicationsFromAllu()
        logger.warn { "Unlinked all applications from Allu." }
    }

    /**
     * Temporary for moving hanke attachment content to cloud to make testing easier. Can be removed
     * in HAI-1964.
     */
    @PostMapping("/move-hanke-attachment-to-cloud/{attachmentId}")
    @SecurityRequirement(name = "bearerAuth")
    fun moveHankeAttachmentToCloud(@PathVariable attachmentId: UUID): AttachmentCloudPath {
        val path = hankeAttachmentService.moveToCloud(attachmentId)
        logger.info { "Moving attachment content to cloud for hanke attachment $attachmentId" }
        return AttachmentCloudPath(path)
    }

    data class AttachmentCloudPath(val path: String)
}
