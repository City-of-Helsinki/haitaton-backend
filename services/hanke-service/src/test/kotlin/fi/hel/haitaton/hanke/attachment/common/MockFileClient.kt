package fi.hel.haitaton.hanke.attachment.common

import com.azure.core.util.BinaryData
import fi.hel.haitaton.hanke.attachment.azure.Container
import fi.hel.haitaton.hanke.attachment.azure.UnexpectedSubdirectoryException
import java.util.EnumMap
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.stereotype.Component

@Component
@Profile("test")
class MockFileClient : FileClient {
    private val fileMap: EnumMap<Container, MutableMap<String, TestFile>> =
        EnumMap(Container::class.java)

    private val subfolderRegex = Regex("/.*/")

    /** Mock client can simulate being connected or disconnected. */
    var connected = true

    fun recreateContainers() {
        Container.entries.forEach { fileMap[it] = mutableMapOf() }
    }

    fun removeContainers() {
        Container.entries.forEach { fileMap[it] = mutableMapOf() }
    }

    fun clearContainers() {
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
                "attachment; filename*=UTF-8''${encodeFilename(originalFilename)}",
                BinaryData.fromBytes(content),
            )
    }

    override fun download(container: Container, path: String): DownloadResponse =
        fileMap[container]!![path]?.toDownloadResponse()
            ?: throw DownloadNotFoundException(path, container)

    override fun delete(container: Container, path: String): Boolean =
        if (connected) fileMap[container]!!.remove(path) != null else error("Not connected")

    override fun deleteAllByPrefix(container: Container, prefix: String) {
        if (!connected) throw IllegalStateException("Not connected")
        val files = fileMap[container]!!
        val paths = files.keys.filter { it.startsWith(prefix) }
        paths
            .find { it.contains(subfolderRegex) }
            ?.let { throw UnexpectedSubdirectoryException(container, it) }
        paths.forEach { files.remove(it) }
    }

    fun listBlobs(container: Container): List<TestFile> = fileMap[container]!!.values.toList()

    fun list(container: Container, prefix: String): List<String> =
        fileMap[container]!!.keys.filter { it.startsWith(prefix) }
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
