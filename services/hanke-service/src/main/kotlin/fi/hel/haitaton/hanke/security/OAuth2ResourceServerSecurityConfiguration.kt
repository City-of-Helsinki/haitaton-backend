package fi.hel.haitaton.hanke.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.ParameterizedTypeReference
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal
import org.springframework.security.oauth2.core.user.DefaultOAuth2User
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class OAuth2ResourceServerSecurityConfiguration(
    @Value("\${security.oauth2.resource.user-info-uri}") private val userinfoUri: String,
) {
    @Bean
    fun introspector(): OpaqueTokenIntrospector {
        return UserInfoOpaqueTokenIntrospector(userinfoUri)
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        AccessRules.configureHttpAccessRules(http)
        http.oauth2ResourceServer { resourceServer: OAuth2ResourceServerConfigurer<HttpSecurity?> ->
            resourceServer.opaqueToken { opaqueToken -> opaqueToken.introspector(introspector()) }
        }
        return http.build()
    }
}

class UserInfoOpaqueTokenIntrospector(private val userinfoUri: String) : OpaqueTokenIntrospector {
    private val rest: WebClient = WebClient.create()

    override fun introspect(token: String): OAuth2AuthenticatedPrincipal {
        val attributes =
            rest
                .get()
                .uri(userinfoUri)
                .headers { it.setBearerAuth(token) }
                .retrieve()
                .bodyToMono(object : ParameterizedTypeReference<Map<String, String>>() {})
                .block()

        return DefaultOAuth2User(listOf(), attributes, "sub")
    }
}
