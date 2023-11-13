package fi.hel.haitaton.hanke.attachment.azure

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
    @Value("\${haitaton.azure.blob.connection-string}") private val connectionString: String,
) {
    @Bean
    fun blobServiceClient(): BlobServiceClient {
        logger.info { "Creating BlobServiceClient" }
        logger.info { "Connection string is $connectionString" }
        return BlobServiceClientBuilder().connectionString(connectionString).buildClient()
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
