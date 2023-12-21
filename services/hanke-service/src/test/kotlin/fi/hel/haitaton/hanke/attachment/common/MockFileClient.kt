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
                "attachment; filename=$originalFilename",
                BinaryData.fromBytes(content)
            )
    }

    override fun download(container: Container, path: String): DownloadResponse =
        fileMap[container]!![path]?.toDownloadResponse()
            ?: throw DownloadNotFoundException(path, container)

    override fun delete(container: Container, path: String): Boolean =
        fileMap[container]!!.remove(path) != null

    override fun deleteAllByPrefix(container: Container, prefix: String) {
        val files = fileMap[container]!!
        val paths = files.keys.filter { it.startsWith(prefix) }
        paths
            .find { it.contains(subfolderRegex) }
            ?.let { throw UnexpectedSubdirectoryException(container, it) }
        paths.forEach { files.remove(it) }
    }

    fun listBlobs(container: Container): List<TestFile> = fileMap[container]!!.values.toList()

    override fun exists(container: Container, path: String): Boolean =
        fileMap[container]!!.containsKey(path)

    override fun existsByPrefix(container: Container, prefix: String): Boolean =
        fileMap[container]!!.keys.any { it.startsWith(prefix) }
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
