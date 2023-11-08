package fi.hel.haitaton.hanke.attachment.common

import fi.hel.haitaton.hanke.attachment.azure.Container
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach

open class MockFileClientTest : FileClientTest() {

    override val fileClient: MockFileClient = Companion.fileClient

    @BeforeEach
    fun cleanUp() {
        fileClient.clearContainers()
    }

    override fun listBlobs(container: Container): List<TestFile> =
        Companion.fileClient.listBlobs(container)

    companion object {
        private val fileClient = MockFileClient()

        @JvmStatic
        @BeforeAll
        fun setup() {
            fileClient.recreateContainers()
        }
    }
}
