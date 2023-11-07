package fi.hel.haitaton.hanke.attachment.common

import fi.hel.haitaton.hanke.attachment.azure.Container
import org.junit.jupiter.api.extension.RegisterExtension

open class MockFileClientTest : FileClientTest() {

    override val fileClient: MockFileClient = Companion.fileClient

    override fun listBlobs(container: Container): List<TestFile> =
        Companion.fileClient.listBlobs(container)

    companion object {
        @JvmField @RegisterExtension val fileClient = MockFileClient()
    }
}
