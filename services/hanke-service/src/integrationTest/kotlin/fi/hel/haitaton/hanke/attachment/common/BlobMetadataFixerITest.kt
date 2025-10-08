package fi.hel.haitaton.hanke.attachment.common

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.models.BlobHttpHeaders
import fi.hel.haitaton.hanke.attachment.azure.AzureContainerServiceClient
import fi.hel.haitaton.hanke.attachment.azure.AzuriteTestContainer
import fi.hel.haitaton.hanke.attachment.azure.BlobFileClient
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.azure.Containers
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BlobMetadataFixerITest {

    private lateinit var blobServiceClient: BlobServiceClient
    private lateinit var fileClient: BlobFileClient
    private lateinit var fixer: BlobMetadataFixer

    // Use separate test container names to avoid interfering with other tests
    private val containers: Containers =
        Containers(
            decisions = "paatokset-fixer-test",
            hakemusAttachments = "hakemusliitteet-fixer-test",
            hankeAttachments = "hankeliitteet-fixer-test",
        )

    @BeforeAll
    fun setup() {
        val connectionString = AzuriteTestContainer.getConnectionString()
        blobServiceClient = AzureContainerServiceClient(connectionString, "").blobServiceClient()
        fileClient = BlobFileClient(blobServiceClient, containers)
        fixer = BlobMetadataFixer(blobServiceClient, containers)

        for (container in containers) {
            blobServiceClient.getBlobContainerClient(container).createIfNotExists()
        }
    }

    @BeforeEach
    fun cleanupBlobs() {
        // Clean up any existing test blobs
        for (containerName in containers) {
            val containerClient = blobServiceClient.getBlobContainerClient(containerName)
            containerClient.listBlobs().forEach { blob ->
                if (blob.name.startsWith("test-fixer/")) {
                    containerClient.getBlobClient(blob.name).delete()
                }
            }
        }
    }

    @Test
    fun `fixAllBlobs scans all containers and returns combined statistics`() {
        // Upload test blobs with properly encoded filenames
        fileClient.upload(
            Container.HANKE_LIITTEET,
            "test-fixer/file1.pdf",
            "file1.pdf",
            MediaType.APPLICATION_PDF,
            "content1".toByteArray(),
        )
        fileClient.upload(
            Container.HAKEMUS_LIITTEET,
            "test-fixer/file2.pdf",
            "file2.pdf",
            MediaType.APPLICATION_PDF,
            "content2".toByteArray(),
        )

        val result = fixer.fixAllBlobs()

        // Should scan at least the 2 test blobs we uploaded
        assertThat(result.scannedCount).isEqualTo(2)
        // These are already properly encoded, so no fixes needed
        assertThat(result.fixedCount).isEqualTo(0)
        assertThat(result.errorCount).isEqualTo(0)
    }

    @Test
    fun `fixBlobsInContainer fixes blobs with unencoded commas`() {
        val container = Container.HANKE_LIITTEET
        val path = "test-fixer/file,with,commas.pdf"
        val filename = "file,with,commas.pdf"

        // Upload blob with properly encoded filename
        fileClient.upload(
            container,
            path,
            filename,
            MediaType.APPLICATION_PDF,
            "content".toByteArray(),
        )

        // Manually corrupt the Content-Disposition header to simulate old format
        val containerClient = blobServiceClient.getBlobContainerClient(containers.hankeAttachments)
        val blobClient = containerClient.getBlobClient(path)
        val corruptedHeaders = BlobHttpHeaders()
        corruptedHeaders.setContentType(MediaType.APPLICATION_PDF_VALUE)
        // Simulate old format with unencoded comma
        corruptedHeaders.setContentDisposition("attachment; filename*=UTF-8''file,with,commas.pdf")
        blobClient.setHttpHeaders(corruptedHeaders)

        // Verify it needs fixing
        val beforeProperties = blobClient.properties
        assertThat(BlobMetadataFixer.needsFixing(beforeProperties.contentDisposition)).isTrue()

        // Run fixer
        val result = fixer.fixBlobsInContainer(container)

        // Verify it was fixed
        assertThat(result.scannedCount).isEqualTo(1)
        assertThat(result.fixedCount).isEqualTo(1)
        assertThat(result.errorCount).isEqualTo(0)

        // Verify the header is now properly encoded (no unencoded commas)
        val afterProperties = blobClient.properties
        assertThat(BlobMetadataFixer.needsFixing(afterProperties.contentDisposition)).isFalse()
    }

    @Test
    fun `fixBlobsInContainer fixes blobs with simple filename format`() {
        val container = Container.HANKE_LIITTEET
        val path = "test-fixer/simple.pdf"

        // Upload blob
        fileClient.upload(
            container,
            path,
            "simple.pdf",
            MediaType.APPLICATION_PDF,
            "content".toByteArray(),
        )

        // Manually set old simple format
        val containerClient = blobServiceClient.getBlobContainerClient(containers.hankeAttachments)
        val blobClient = containerClient.getBlobClient(path)
        val oldHeaders = BlobHttpHeaders()
        oldHeaders.setContentType(MediaType.APPLICATION_PDF_VALUE)
        oldHeaders.setContentDisposition("attachment; filename=\"simple.pdf\"")
        blobClient.setHttpHeaders(oldHeaders)

        // Verify it needs fixing
        val beforeProperties = blobClient.properties
        assertThat(BlobMetadataFixer.needsFixing(beforeProperties.contentDisposition)).isTrue()

        // Run fixer
        val result = fixer.fixBlobsInContainer(container)

        // Verify it was fixed
        assertThat(result.scannedCount).isEqualTo(1)
        assertThat(result.fixedCount).isEqualTo(1)
        assertThat(result.errorCount).isEqualTo(0)

        // Verify the header now includes RFC 5987 format
        val afterProperties = blobClient.properties
        assertThat(BlobMetadataFixer.needsFixing(afterProperties.contentDisposition)).isFalse()
        assertThat(afterProperties.contentDisposition).contains("filename*=UTF-8''simple.pdf")
    }

    @Test
    fun `fixBlobsInContainer skips blobs with no Content-Disposition`() {
        val container = Container.HANKE_LIITTEET
        val path = "test-fixer/no-disposition.pdf"

        // Upload blob
        fileClient.upload(
            container,
            path,
            "test.pdf",
            MediaType.APPLICATION_PDF,
            "content".toByteArray(),
        )

        // Remove Content-Disposition
        val containerClient = blobServiceClient.getBlobContainerClient(containers.hankeAttachments)
        val blobClient = containerClient.getBlobClient(path)
        val headers = BlobHttpHeaders()
        headers.setContentType(MediaType.APPLICATION_PDF_VALUE)
        headers.setContentDisposition(null)
        blobClient.setHttpHeaders(headers)

        // Run fixer
        val result = fixer.fixBlobsInContainer(container)

        // Should scan but not fix (just warn)
        assertThat(result.scannedCount).isEqualTo(1)
        assertThat(result.fixedCount).isEqualTo(0)
        assertThat(result.errorCount).isEqualTo(0)
    }

    @Test
    fun `fixBlobsInContainer counts errors when filename extraction fails`() {
        val container = Container.HANKE_LIITTEET
        val path = "test-fixer/invalid-disposition.pdf"

        // Upload blob
        fileClient.upload(
            container,
            path,
            "test.pdf",
            MediaType.APPLICATION_PDF,
            "content".toByteArray(),
        )

        // Set invalid Content-Disposition that can't be parsed
        val containerClient = blobServiceClient.getBlobContainerClient(containers.hankeAttachments)
        val blobClient = containerClient.getBlobClient(path)
        val headers = BlobHttpHeaders()
        headers.setContentType(MediaType.APPLICATION_PDF_VALUE)
        headers.setContentDisposition("invalid-disposition-format")
        blobClient.setHttpHeaders(headers)

        // Run fixer
        val result = fixer.fixBlobsInContainer(container)

        // Should scan and encounter error (can't extract filename)
        assertThat(result.scannedCount).isEqualTo(1)
        assertThat(result.fixedCount).isEqualTo(0)
        assertThat(result.errorCount).isEqualTo(1)
    }

    @Test
    fun `fixBlobsInContainer skips properly encoded blobs`() {
        val container = Container.HANKE_LIITTEET
        val path = "test-fixer/already-encoded.pdf"

        // Upload blob with properly encoded filename containing special characters
        fileClient.upload(
            container,
            path,
            "file,with,commas.pdf",
            MediaType.APPLICATION_PDF,
            "content".toByteArray(),
        )

        // Verify it's properly encoded
        val containerClient = blobServiceClient.getBlobContainerClient(containers.hankeAttachments)
        val blobClient = containerClient.getBlobClient(path)
        val beforeProperties = blobClient.properties
        assertThat(BlobMetadataFixer.needsFixing(beforeProperties.contentDisposition)).isFalse()

        // Run fixer
        val result = fixer.fixBlobsInContainer(container)

        // Should scan but not fix
        assertThat(result.scannedCount).isEqualTo(1)
        assertThat(result.fixedCount).isEqualTo(0)
        assertThat(result.errorCount).isEqualTo(0)

        // Verify header unchanged
        val afterProperties = blobClient.properties
        assertThat(afterProperties.contentDisposition)
            .isEqualTo(beforeProperties.contentDisposition)
    }
}
