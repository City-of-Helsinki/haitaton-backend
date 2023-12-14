package fi.hel.haitaton.hanke.profiili

import fi.hel.haitaton.hanke.accessToken
import fi.hel.haitaton.hanke.getResourceAsText
import fi.hel.haitaton.hanke.toJsonString
import mu.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.security.core.context.SecurityContext
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.body
import reactor.core.publisher.Mono

private val logger = KotlinLogging.logger {}

@Component
@Profile("!test")
class ProfiiliClient(
    private val properties: ProfiiliProperties,
    webClientBuilder: WebClient.Builder,
) {
    private val webClient: WebClient = webClientBuilder.build()
    private val myProfileGraphQl = MY_PROFILE_QUERY_FILE.getResourceAsText()
    private val myProfileQuery = GraphQlQuery(myProfileGraphQl, MY_PROFILE_OPERATION).toJsonString()

    fun getVerifiedName(securityContext: SecurityContext): Names {
        logger.info { "Getting user's verified name from Profiili." }
        val apiToken = authenticate(securityContext)
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

    private fun authenticate(securityContext: SecurityContext): String {
        val accessToken =
            securityContext.accessToken() ?: throw VerifiedNameNotFound("User not authenticated.")

        val apiTokens = getApiTokens(accessToken)

        return apiTokens[properties.audience]
            ?: throw VerifiedNameNotFound("Profiili audience not found from API tokens.")
    }

    private fun getApiTokens(accessToken: String): Map<String, String> {
        return webClient
            .get()
            .uri(properties.apiTokensUrl)
            .accept(MediaType.APPLICATION_JSON)
            .headers { it.setBearerAuth(accessToken) }
            .retrieve()
            .bodyToMono(object : ParameterizedTypeReference<Map<String, String>>() {})
            .block()!!
    }

    companion object {
        const val MY_PROFILE_QUERY_FILE = "/graphql/MyProfileQuery.graphql"
        const val MY_PROFILE_OPERATION = "MyProfileQuery"
    }

    data class GraphQlQuery(
        val query: String,
        val operationName: String,
        val variables: Map<String, Any>? = null
    )
}

class VerifiedNameNotFound(reason: String) :
    RuntimeException("Verified name of user could not be obtained. $reason")
