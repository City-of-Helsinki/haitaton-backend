package fi.hel.haitaton.hanke.profiili

import com.fasterxml.jackson.databind.JsonNode
import fi.hel.haitaton.hanke.getResourceAsText
import fi.hel.haitaton.hanke.toJsonString
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.body
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono

private val logger = KotlinLogging.logger {}

@Component
@Profile("!test")
class ProfiiliClient(
    private val properties: ProfiiliProperties,
    webClientBuilder: WebClient.Builder,
    @Value("\${spring.security.oauth2.resourceserver.jwt.issuer-uri}") private val issuer: String,
) {
    private val webClient: WebClient = webClientBuilder.build()
    private val myProfileGraphQl = MY_PROFILE_QUERY_FILE.getResourceAsText()
    private val myProfileQuery = GraphQlQuery(myProfileGraphQl, MY_PROFILE_OPERATION).toJsonString()

    private var tokenUri: String? = null

    fun getVerifiedName(accessToken: String): Names {
        logger.info { "Getting user's verified name from Profiili." }
        val apiToken = authenticate(accessToken)
        val profiiliData = queryForVerifiedName(apiToken, myProfileQuery)
        logger.info { "Got user's verified name from Profiili." }
        return profiiliData.data.myProfile?.verifiedPersonalInformation
            ?: throw VerifiedNameNotFound("Verified name not found from profile.")
    }

    private fun queryForVerifiedName(apiToken: String, query: String): ProfiiliResponse {
        return webClient
            .post()
            .uri(properties.graphQlUrl)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .headers { it.setBearerAuth(apiToken) }
            .body(Mono.just(query))
            .retrieve()
            .bodyToMono(object : ParameterizedTypeReference<ProfiiliResponse>() {})
            .block()!!
    }

    private fun authenticate(accessToken: String): String {
        val apiTokens = getApiTokens(accessToken)
        return apiTokens["access_token"]?.asText()
            ?: throw VerifiedNameNotFound("Token response did not contain an access token.")
    }

    private fun getApiTokens(accessToken: String): JsonNode {
        val uri = tokenUri ?: getTokenApiUrl()

        return webClient
            .post()
            .uri(uri)
            .headers { it.setBearerAuth(accessToken) }
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(
                BodyInserters.fromFormData("audience", properties.audience)
                    .with("grant_type", TOKEN_API_GRANT_TYPE)
                    .with("permission", TOKEN_API_PERMISSION)
            )
            .retrieve()
            // handle 401 errors specifically to give a more informative error message
            .onStatus({ responseStatus -> responseStatus == HttpStatus.UNAUTHORIZED }) { response ->
                response.bodyToMono<String>().flatMap { body ->
                    logger.error { "Error from Profiili API call. Response status=401, body=$body" }
                    Mono.error(UnauthorizedException(body))
                }
            }
            .bodyToMono(JsonNode::class.java)
            .doOnError(WebClientResponseException::class.java) { ex ->
                logger.error {
                    "Error from Profiili API call. Response status=${ex.statusCode}, body=${ex.responseBodyAsString}"
                }
            }
            .block()!!
    }

    private fun getTokenApiUrl(): String {
        logger.info { "Loading Profiili token URI from OpenID configuration..." }
        val configurationUri = "$issuer/.well-known/openid-configuration"

        val conf =
            webClient
                .get()
                .uri(configurationUri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(JsonNode::class.java)
                .onErrorMap(WebClientResponseException::class.java) { ex ->
                    ProfiiliConfigurationError(
                        "Unable to load OpenID configuration. " +
                            "Response status=${ex.statusCode}, " +
                            "body=${ex.responseBodyAsString}",
                        ex,
                    )
                }
                .block()!!

        val uri =
            conf["token_endpoint"]?.asText()
                ?: throw ProfiiliConfigurationError(
                    "OpenID configuration didn't contain a token endpoint."
                )
        tokenUri = uri
        logger.info { "Got Profiili token URI: $uri" }
        return uri
    }

    companion object {
        const val MY_PROFILE_QUERY_FILE = "/graphql/MyProfileQuery.graphql"
        const val MY_PROFILE_OPERATION = "MyProfileQuery"
        const val TOKEN_API_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:uma-ticket"
        const val TOKEN_API_PERMISSION = "#access"
    }

    data class GraphQlQuery(
        val query: String,
        val operationName: String,
        val variables: Map<String, Any>? = null,
    )
}

class VerifiedNameNotFound(reason: String) :
    RuntimeException("Verified name of user could not be obtained. $reason")

class UnauthorizedException(reason: String) :
    RuntimeException("Profiili API token request was unauthorized. $reason")

class ProfiiliConfigurationError(reason: String, cause: Exception? = null) :
    RuntimeException("Error in Profiili API connection: $reason", cause)
