package fi.hel.haitaton.hanke.attachment.azure

import com.azure.identity.DefaultAzureCredentialBuilder
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.BlobServiceClientBuilder
import kotlin.reflect.full.memberProperties
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

private val logger = KotlinLogging.logger {}

@Configuration
@Profile("!test")
class AzureContainerServiceClient(
    @Value("\${haitaton.azure.blob.connection-string}") private val connectionString: String?,
    @Value("\${haitaton.azure.blob.endpoint}") private val endpoint: String,
) {
    @Bean
    fun blobServiceClient(): BlobServiceClient {
        logger.info { "Creating BlobServiceClient" }
        return if (connectionString != null) {
            logger.info { "Connecting using a connection string (local development)" }
            logger.info { "Connection string is $connectionString" }
            BlobServiceClientBuilder().connectionString(connectionString).buildClient()
        } else {
            logger.info { "Connecting using a default credential provider (cloud environments)" }
            logger.info { "Endpoint is $endpoint" }
            BlobServiceClientBuilder()
                .endpoint(endpoint)
                .credential(DefaultAzureCredentialBuilder().build())
                .buildClient()
        }
    }
}

@ConfigurationProperties(prefix = "haitaton.azure.blob")
data class Containers(
    val decisions: String,
    val hakemusAttachments: String,
    val hankeAttachments: String,
) : Iterable<String> {
    override fun iterator(): Iterator<String> =
        Containers::class.memberProperties.map { it.get(this) as String }.iterator()
}

enum class Container {
    HAKEMUS_LIITTEET,
    HANKE_LIITTEET,
    PAATOKSET,
}
