package fi.hel.haitaton.hanke.attachment.common

import fi.hel.haitaton.hanke.attachment.azure.Container
import org.junit.jupiter.api.extension.RegisterExtension

open class MockFileClientTest : FileClientTest() {

    override val fileClient: MockFileClient = mockFileClientExtension.client

    override fun listBlobs(container: Container): List<TestFile> = fileClient.listBlobs(container)

    companion object {
        @JvmField @RegisterExtension val mockFileClientExtension = MockFileClientExtension.create()
    }
}
