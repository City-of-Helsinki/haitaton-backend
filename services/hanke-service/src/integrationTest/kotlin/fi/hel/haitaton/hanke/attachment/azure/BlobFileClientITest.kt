package fi.hel.haitaton.hanke.attachment.azure

import com.azure.core.util.BinaryData
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClient
import fi.hel.haitaton.hanke.attachment.common.FileClientTest
import fi.hel.haitaton.hanke.attachment.common.TestFile
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BlobFileClientITest : FileClientTest() {

    private lateinit var azuriteContainer: GenericContainer<*>
    private lateinit var serviceClient: BlobServiceClient
    private lateinit var hankeAttachmentClient: BlobContainerClient

    override lateinit var fileClient: BlobFileClient

    private val containers: Containers =
        Containers(
            decisions = "paatokset-test",
            hakemusAttachments = "hakemusliitteet-test",
            hankeAttachments = "hankeliitteet-test"
        )

    @BeforeAll
    fun setup() {
        azuriteContainer =
            GenericContainer(DockerImageName.parse("mcr.microsoft.com/azure-storage/azurite"))
                .withExposedPorts(10000)
        azuriteContainer.start()
        val connectionString =
            "BlobEndpoint=http://${azuriteContainer.host}:${azuriteContainer.firstMappedPort}/devstoreaccount1;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;"
        serviceClient = AzureContainerServiceClient(connectionString, "").blobServiceClient()
        hankeAttachmentClient = serviceClient.getBlobContainerClient(containers.hankeAttachments)

        fileClient = BlobFileClient(serviceClient, containers)

        for (container in containers) {
            serviceClient.getBlobContainerClient(container).createIfNotExists()
        }
    }

    @BeforeEach
    fun clearContainers() {
        for (container in containers) {
            val containerClient = serviceClient.getBlobContainerClient(container)

            containerClient.listBlobs().forEach { containerClient.getBlobClient(it.name).delete() }
        }
    }

    @AfterAll
    fun teardown() {
        azuriteContainer.stop()
    }

    override fun listBlobs(container: Container): List<TestFile> =
        when (container) {
                Container.HAKEMUS_LIITTEET ->
                    serviceClient.getBlobContainerClient(containers.hakemusAttachments)
                Container.HANKE_LIITTEET ->
                    serviceClient.getBlobContainerClient(containers.hankeAttachments)
                Container.PAATOKSET -> serviceClient.getBlobContainerClient(containers.decisions)
            }
            .listBlobs()
            .map {
                TestFile(
                    it.name,
                    MediaType.parseMediaType(it.properties.contentType),
                    it.properties.contentLength.toInt(),
                    it.properties.contentDisposition,
                    BinaryData.fromString("")
                )
            }
            .toList()
}
