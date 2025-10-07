package fi.hel.haitaton.hanke.attachment.common

import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.models.BlobHttpHeaders
import fi.hel.haitaton.hanke.attachment.azure.Container
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import mu.KotlinLogging
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

private val logger = KotlinLogging.logger {}

private const val CONTENT_DISPOSITION_PREFIX = "filename*=UTF-8''"

/**
 * One-time utility to fix Content-Disposition headers for blobs that were uploaded before the
 * filename encoding fix. This component is disabled by default and should only be enabled
 * temporarily when needed.
 *
 * To enable, set environment variable: `HAITATON_ATTACHMENT_NAME_FIX_ENABLED=true`
 *
 * The fixer will:
 * 1. Scan all blobs in all containers on application startup
 * 2. Identify blobs with improperly encoded filenames in Content-Disposition headers
 * 3. Re-encode the filename using RFC 5987 encoding (via Spring's ContentDisposition)
 * 4. Update the blob metadata (does not re-upload content)
 *
 * After running successfully once, the environment variable should be removed to prevent
 * unnecessary processing on subsequent startups.
 */
@Component
@ConditionalOnProperty(
    name = ["haitaton.attachment-name-fix.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class BlobMetadataFixer(private val blobServiceClient: BlobServiceClient) {

    companion object {
        /**
         * Determines if a Content-Disposition header needs fixing. A header needs fixing if:
         * - It doesn't use RFC 5987 encoding (filename* parameter)
         * - It contains unencoded special characters like commas, semicolons, etc.
         */
        fun needsFixing(contentDisposition: String): Boolean {
            // If it already uses RFC 5987 encoding (filename*=UTF-8''), check for issues
            if (contentDisposition.contains(CONTENT_DISPOSITION_PREFIX)) {
                // Extract the encoded part
                val encodedPart =
                    contentDisposition.substringAfter(CONTENT_DISPOSITION_PREFIX).trim()
                // If we find unencoded special characters, it needs fixing
                return encodedPart.contains(',') ||
                    encodedPart.contains(';') ||
                    encodedPart.contains('"') ||
                    encodedPart.contains('\'') ||
                    encodedPart.contains('*')
            }

            // If it doesn't use RFC 5987 encoding at all, it needs fixing
            return true
        }

        /**
         * Extracts the original filename from a Content-Disposition header. Handles both:
         * - RFC 5987 format: attachment; filename*=UTF-8''encoded-name
         * - Simple format: attachment; filename="name"
         */
        fun extractOriginalFilename(contentDisposition: String): String? {
            // Try RFC 5987 format first - look for filename*=UTF-8''
            if (contentDisposition.contains(CONTENT_DISPOSITION_PREFIX)) {
                val afterMarker = contentDisposition.substringAfter(CONTENT_DISPOSITION_PREFIX)
                // Extract until semicolon or end of string
                val encodedFilename = afterMarker.substringBefore(';').trim()
                return try {
                    URLDecoder.decode(encodedFilename, StandardCharsets.UTF_8)
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to decode filename: $encodedFilename" }
                    null
                }
            }

            // Try simple filename format
            val simpleMatch =
                Regex("""filename="([^"]+)"""").find(contentDisposition)
                    ?: Regex("""filename=([^;]+)""").find(contentDisposition)

            return simpleMatch?.groupValues?.get(1)?.trim()
        }
    }

    @EventListener(ApplicationReadyEvent::class)
    fun runFixOnStartup() {
        logger.warn { "Blob metadata fixer is enabled and will run on startup" }
        try {
            val result = fixAllBlobs()
            logger.warn {
                "Blob metadata fix completed successfully. " +
                    "Scanned: ${result.scannedCount}, Fixed: ${result.fixedCount}, Errors: ${result.errorCount}"
            }
            if (result.errorCount > 0) {
                logger.error {
                    "Blob metadata fix completed with ${result.errorCount} errors. Check logs for details."
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Blob metadata fix failed with exception: ${e.message}" }
        }
    }

    /**
     * Scans and fixes all blobs in all containers. This should be called manually or via a
     * scheduled task.
     */
    fun fixAllBlobs(): BlobFixResult {
        logger.info { "Starting blob metadata fixer for all containers" }

        val results = Container.entries.map { container -> fixBlobsInContainer(container) }

        val totalResult =
            BlobFixResult(
                scannedCount = results.sumOf { it.scannedCount },
                fixedCount = results.sumOf { it.fixedCount },
                errorCount = results.sumOf { it.errorCount },
            )

        logger.info {
            "Blob metadata fixer completed. Scanned: ${totalResult.scannedCount}, " +
                "Fixed: ${totalResult.fixedCount}, Errors: ${totalResult.errorCount}"
        }

        return totalResult
    }

    /** Fixes blobs in a specific container. Returns statistics about the operation. */
    fun fixBlobsInContainer(container: Container): BlobFixResult {
        logger.info { "Scanning container: $container" }

        val containerClient = getContainerClient(container)
        var scannedCount = 0
        var fixedCount = 0
        var errorCount = 0

        containerClient.listBlobs().forEach { blobItem ->
            scannedCount++

            try {
                val blobClient = containerClient.getBlobClient(blobItem.name)
                val properties = blobClient.properties
                val currentDisposition = properties.contentDisposition

                if (currentDisposition == null) {
                    logger.warn { "Blob ${blobItem.name} has no Content-Disposition header" }
                    return@forEach
                }

                // Check if the Content-Disposition needs fixing
                if (needsFixing(currentDisposition)) {
                    val originalFilename = extractOriginalFilename(currentDisposition)

                    if (originalFilename != null) {
                        // Re-encode the filename properly using Spring's ContentDisposition
                        val fixedDisposition =
                            HeadersBuilder.buildHeaders(originalFilename, properties.contentType)
                                .contentDisposition
                                .toString()

                        // Update the blob HTTP headers
                        val newHeaders = BlobHttpHeaders()
                        newHeaders.setContentType(properties.contentType)
                        newHeaders.setContentDisposition(fixedDisposition)
                        blobClient.setHttpHeaders(newHeaders)

                        logger.info {
                            "Fixed blob ${blobItem.name}: '$currentDisposition' -> '$fixedDisposition'"
                        }
                        fixedCount++
                    } else {
                        logger.warn {
                            "Could not extract original filename from: $currentDisposition"
                        }
                        errorCount++
                    }
                }
            } catch (e: Exception) {
                logger.error(e) { "Error processing blob ${blobItem.name}: ${e.message}" }
                errorCount++
            }
        }

        logger.info {
            "Container $container completed. Scanned: $scannedCount, Fixed: $fixedCount, Errors: $errorCount"
        }

        return BlobFixResult(scannedCount, fixedCount, errorCount)
    }

    private fun getContainerClient(container: Container): BlobContainerClient {
        return blobServiceClient.getBlobContainerClient(
            container.name.lowercase().replace('_', '-')
        )
    }
}

data class BlobFixResult(val scannedCount: Int, val fixedCount: Int, val errorCount: Int)
