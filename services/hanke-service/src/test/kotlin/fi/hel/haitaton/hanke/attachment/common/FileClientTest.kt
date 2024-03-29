package fi.hel.haitaton.hanke.attachment.common

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.each
import assertk.assertions.extracting
import assertk.assertions.hasClass
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import assertk.assertions.messageContains
import assertk.assertions.prop
import assertk.assertions.single
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.azure.UnexpectedSubdirectoryException
import fi.hel.haitaton.hanke.hasSameElementsAs
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.MediaType

/**
 * This is shared test class for [fi.hel.haitaton.hanke.attachment.azure.BlobFileClientITest] and
 * [MockFileClientTest] to make sure they have the same behaviour.
 */
abstract class FileClientTest {
    private val testContent = "This was created in an integration test".toByteArray()
    private val container = Container.HANKE_LIITTEET
    abstract val fileClient: FileClient

    @Nested
    inner class Upload {

        @ParameterizedTest
        @ValueSource(strings = ["test/test.txt", "stuff/other.txt", "43/file.pdf"])
        fun `uploads the file to the correct path`(path: String) {
            fileClient.upload(container, path, "test.txt", MediaType.TEXT_PLAIN, testContent)

            assertThat(listBlobs()).single().prop(TestFile::path).isEqualTo(path)
        }

        @ParameterizedTest
        @CsvSource(
            "test.txt,test.txt",
            "åäö.txt,%C3%A5%C3%A4%C3%B6.txt",
            "1 2 3 €.pdf,1%202%203%20%E2%82%AC.pdf"
        )
        fun `uploads a file with the correct content disposition`(
            originalFileName: String,
            encodedFileName: String
        ) {
            fileClient.upload(
                container,
                "test/$originalFileName",
                originalFileName,
                MediaType.TEXT_PLAIN,
                testContent
            )

            assertThat(listBlobs())
                .single()
                .prop(TestFile::contentDisposition)
                .isEqualTo("attachment; filename*=UTF-8''$encodedFileName")
        }

        @ParameterizedTest
        @ValueSource(strings = ["text/plain", "application/pdf", "image/png", "image/x-dwg"])
        fun `uploads a file with the correct content type`(mediaTypeString: String) {
            val mediaType = MediaType.parseMediaType(mediaTypeString)

            fileClient.upload(container, "test/test.txt", "test.txt", mediaType, testContent)

            assertThat(listBlobs()).single().prop(TestFile::contentType).isEqualTo(mediaType)
        }

        @Test
        fun `uploads several files to different paths`() {
            val paths = listOf("test/test.txt", "test/test.png", "test/test.pdf")

            paths.forEach {
                fileClient.upload(container, it, "test.txt", MediaType.TEXT_PLAIN, testContent)
            }

            val blobs = listBlobs()
            assertThat(blobs).hasSize(3)
            assertThat(blobs)
                .extracting { it.path }
                .containsExactlyInAnyOrder("test/test.txt", "test/test.png", "test/test.pdf")
        }

        @Test
        fun `overwrites file when uploading a file with an existing path`() {
            val path = "test/test.txt"
            fileClient.upload(container, path, "test.txt", MediaType.TEXT_PLAIN, testContent)
            val newContent = "This is not the original content".toByteArray()
            val newFilename = "other.txt"
            val newMediaType = MediaType.TEXT_MARKDOWN

            fileClient.upload(container, path, newFilename, newMediaType, newContent)

            assertThat(listBlobs()).single().all {
                prop(TestFile::path).isEqualTo(path)
                prop(TestFile::contentDisposition)
                    .isEqualTo("attachment; filename*=UTF-8''$newFilename")
                prop(TestFile::contentType).isEqualTo(newMediaType)
            }
            val content = fileClient.download(container, path).content.toBytes()
            assertThat(content).isEqualTo(newContent)
        }

        @ParameterizedTest
        @EnumSource(Container::class)
        fun `uploads to the given container`(container: Container) {
            val path = "test/test.txt"
            fileClient.upload(container, path, "test.txt", MediaType.TEXT_PLAIN, testContent)

            assertThat(listBlobs(container)).single().prop(TestFile::path).isEqualTo(path)
            assertThat(listBlobsFromOtherContainers(container)).isEmpty()
        }
    }

    @Nested
    inner class Download {
        private val path = "test/test.dwg"

        @Test
        fun `throw exception when blob not found`() {
            assertFailure { fileClient.download(container, path) }
                .all {
                    hasClass(DownloadNotFoundException::class)
                    messageContains("path=$path")
                    messageContains("container=$container")
                }
        }

        @Test
        fun `downloads the file along with content type and length`() {
            val contentType = MediaType.parseMediaType("image/x-dwg")
            fileClient.upload(container, path, "test.dwg", contentType, testContent)

            val result = fileClient.download(container, path)

            assertThat(result.content.toBytes()).isEqualTo(testContent)
            assertThat(result.contentType).isEqualTo(contentType)
            assertThat(result.contentLength).isEqualTo(testContent.size)
        }

        @ParameterizedTest
        @EnumSource(Container::class)
        fun `download from the given container`(container: Container) {
            val contentType = MediaType.parseMediaType("image/x-dwg")
            fileClient.upload(container, path, "test.dwg", contentType, testContent)

            val result = fileClient.download(container, path)

            assertThat(result.content.toBytes()).isEqualTo(testContent)
        }
    }

    @Nested
    inner class Delete {
        private val originalFileName = "test.txt"
        private val mediaType = MediaType.TEXT_PLAIN
        private val path = "test/test.txt"

        @Test
        fun `returns false when blob not found`() {
            assertThat(fileClient.delete(container, path)).isFalse()
        }

        @Test
        fun `deletes the uploaded file`() {
            fileClient.upload(container, path, originalFileName, mediaType, testContent)

            val response = fileClient.delete(container, path)

            assertThat(response).isTrue()
            assertThat(listBlobs()).isEmpty()
        }

        @Test
        fun `deletes the correct file from several uploaded files`() {
            val targetPath = "test/test.txt"
            val otherPath = "test/test2.txt"
            val thirdPath = "test/test3.txt"
            fileClient.upload(container, otherPath, originalFileName, mediaType, testContent)
            fileClient.upload(container, targetPath, originalFileName, mediaType, testContent)
            fileClient.upload(container, thirdPath, originalFileName, mediaType, testContent)

            val response = fileClient.delete(container, targetPath)

            assertThat(response).isTrue()
            val blobs = listBlobs()
            assertThat(blobs).hasSize(2)
            assertThat(blobs).extracting { it.path }.containsExactlyInAnyOrder(otherPath, thirdPath)
        }

        @ParameterizedTest
        @EnumSource(Container::class)
        fun `deletes from the given container`(container: Container) {
            Container.entries.forEach {
                fileClient.upload(it, path, originalFileName, mediaType, testContent)
            }
            assertThat(listBlobs(container)).hasSize(1)

            val response = fileClient.delete(container, path)

            assertThat(response).isTrue()
            assertThat(listBlobs(container)).isEmpty()
            assertThat(Container.entries.minus(container).map { listBlobs(it) }).each {
                it.hasSize(1)
            }
        }
    }

    @Nested
    inner class DeleteAllByPrefix {
        private val originalFileName = "test.txt"
        private val mediaType = MediaType.TEXT_PLAIN

        @Test
        fun `does not throw when there are no blobs`() {
            fileClient.deleteAllByPrefix(container, "prefix")
        }

        @Test
        fun `deletes the correct files`() {
            val matchingPaths =
                listOf(
                    "45/file.txt",
                    "45/file2.txt",
                )
            val notMatchingPaths =
                listOf(
                    "file.txt",
                    "45file.txt",
                    "4/file.txt",
                    "44/file.txt",
                    "454/file.txt",
                    "other/45/file.txt",
                )
            for (path in matchingPaths + notMatchingPaths) {
                fileClient.upload(container, path, originalFileName, mediaType, testContent)
            }

            fileClient.deleteAllByPrefix(container, "45/")

            val remainingPaths = listBlobs().map { it.path }
            assertThat(remainingPaths).hasSameElementsAs(notMatchingPaths)
        }

        @ParameterizedTest
        @EnumSource(Container::class)
        fun `deletes from the given container`(container: Container) {
            val path = "45/file.txt"
            Container.entries.forEach {
                fileClient.upload(it, path, originalFileName, mediaType, testContent)
            }
            assertThat(listBlobs(container)).hasSize(1)

            fileClient.deleteAllByPrefix(container, "45/")

            assertThat(listBlobs(container)).isEmpty()
            assertThat(Container.entries.minus(container).map { listBlobs(it) }).each {
                it.hasSize(1)
            }
        }

        @Test
        fun `throws UnexpectedSubdirectoryException with a complex directory structure`() {
            val paths = listOf("45/file.txt", "45/subfolder/file.txt", "45/file2.txt")
            for (path in paths) {
                fileClient.upload(container, path, originalFileName, mediaType, testContent)
            }
            val exception = assertFailure { fileClient.deleteAllByPrefix(container, "45/") }

            exception.all {
                hasClass(UnexpectedSubdirectoryException::class)
                messageContains(container.toString())
                messageContains("45/subfolder/")
            }
            assertThat(listBlobs().map { it.path }).hasSameElementsAs(paths)
        }
    }

    abstract fun listBlobs(container: Container): List<TestFile>

    fun listBlobs(): List<TestFile> = listBlobs(container)

    fun listBlobsFromOtherContainers(container: Container): List<TestFile> =
        Container.entries.minus(container).flatMap { listBlobs(it) }
}
