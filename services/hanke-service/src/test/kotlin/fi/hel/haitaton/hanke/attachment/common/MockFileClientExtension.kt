package fi.hel.haitaton.hanke.attachment.common

import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.springframework.test.context.junit.jupiter.SpringExtension

/**
 * A test extension that sets and cleans up the mock file client.
 *
 * In integration tests where the Spring context contains a MockFileClient, the extension can be
 * used by adding an annotation to the class:
 * ```
 * @ExtendWith(MockFileClientExtension::class)
 * ```
 *
 * The file client is retrieved from the Spring context, so the same client can be accessed by
 * autowiring the file client as a dependency as normal:
 * ```
 * @Autowired private val fileClient: MockFileClient
 * ```
 *
 * For unit tests without a Spring context, the extension can be used by creating and registering
 * the extension in the test's companion object:
 * ```
 * companion object {
 *     @JvmField @RegisterExtension val mockFileClientExtension = MockFileClientExtension.create()
 * }
 * ```
 *
 * In this case, the file client is created for the extension and can be accessed from the
 * extension:
 * ```
 * val fileClient: MockFileClient = mockFileClientExtension.client
 * ```
 */
class MockFileClientExtension : BeforeEachCallback, BeforeAllCallback, AfterAllCallback {
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

    override fun afterAll(context: ExtensionContext?) {
        client.removeContainers()
    }

    companion object {
        fun create(): MockFileClientExtension =
            MockFileClientExtension().apply { client = MockFileClient() }
    }
}
