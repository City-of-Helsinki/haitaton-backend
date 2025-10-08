package fi.hel.haitaton.hanke.attachment.azure

import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

/**
 * Singleton object providing a shared Azurite container for integration tests.
 *
 * The container is lazily initialized on first access and reused across all tests. This avoids the
 * overhead of starting multiple Azurite containers during test execution.
 */
object AzuriteTestContainer {

    /** Shared Azurite container instance. Lazily initialized and started on first access. */
    val container: GenericContainer<*> by lazy {
        GenericContainer(DockerImageName.parse("mcr.microsoft.com/azure-storage/azurite"))
            .withExposedPorts(10000)
            .also { it.start() }
    }

    /**
     * Gets the Azure Blob Storage connection string for the Azurite container.
     *
     * This connection string uses the well-known Azurite development credentials and points to the
     * exposed blob storage endpoint.
     */
    fun getConnectionString(): String {
        return "BlobEndpoint=http://${container.host}:${container.firstMappedPort}/devstoreaccount1;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;"
    }
}
