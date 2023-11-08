package fi.hel.haitaton.hanke.attachment.common

import com.azure.core.util.BinaryData
import fi.hel.haitaton.hanke.attachment.azure.Container
import java.util.EnumMap
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.stereotype.Component

@Component
@Profile("test")
class MockFileClient : FileClient, BeforeAllCallback, BeforeEachCallback {
    private val fileMap: EnumMap<Container, MutableMap<String, TestFile>> =
        EnumMap(Container::class.java)

    override fun beforeAll(context: ExtensionContext?) {
        Container.entries.forEach { fileMap[it] = mutableMapOf() }
    }

    override fun beforeEach(context: ExtensionContext?) {
        Container.entries.forEach { fileMap[it]!!.clear() }
    }

    override fun upload(
        container: Container,
        path: String,
        originalFilename: String,
        contentType: MediaType,
        content: ByteArray,
    ) {
        fileMap[container]!![path] =
            TestFile(
                path,
                contentType,
                content.size,
                "attachment; filename=$originalFilename",
                BinaryData.fromBytes(content)
            )
    }

    override fun download(container: Container, path: String): DownloadResponse =
        fileMap[container]!![path]?.toDownloadResponse()
            ?: throw DownloadNotFoundException(path, container)

    override fun delete(container: Container, path: String): Boolean =
        fileMap[container]!!.remove(path) != null

    internal fun listBlobs(container: Container): List<TestFile> =
        fileMap[container]!!.values.toList()
}

data class TestFile(
    val path: String,
    val contentType: MediaType,
    val contentLength: Int,
    val contentDisposition: String,
    val content: BinaryData,
) {
    fun toDownloadResponse() = DownloadResponse(contentType, contentLength, content)
}
