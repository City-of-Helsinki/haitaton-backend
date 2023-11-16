package fi.hel.haitaton.hanke.attachment.common

import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension

class MockFileClientExtension : BeforeEachCallback, BeforeAllCallback {
    lateinit var client: MockFileClient

    override fun beforeEach(context: ExtensionContext?) {
        client.clearContainers()
    }

    override fun beforeAll(context: ExtensionContext) {
        if (!this::client.isInitialized) {
            client =
                SpringExtension.getApplicationContext(context).getBean(MockFileClient::class.java)
        }

        client.recreateContainers()
    }

    companion object {
        fun create(): MockFileClientExtension =
            MockFileClientExtension().apply { client = MockFileClient() }
    }
}
