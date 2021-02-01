package fi.hel.haitaton.hanke

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.access.expression.SecurityExpressionHandler
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.data.repository.query.SecurityEvaluationContextExtension
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer
import org.springframework.security.oauth2.provider.expression.OAuth2WebSecurityExpressionHandler
import org.springframework.security.oauth2.provider.token.TokenStore
import org.springframework.security.oauth2.provider.token.store.jwk.JwkTokenStore
import org.springframework.security.web.FilterInvocation


@Configuration
@EnableResourceServer
@EnableGlobalMethodSecurity(prePostEnabled = true)
class ResourceServerConfig : ResourceServerConfigurerAdapter() {

    @Value("\${claim.aud}")
    lateinit var claimAud: String

    @Value("\${jwkSetUri}")
    lateinit var urlJwk: String

    override fun configure(resources: ResourceServerSecurityConfigurer) {
        resources.tokenStore(tokenStore())
        resources.resourceId(claimAud)
        resources.expressionHandler(handler())
    }

    override fun configure(http: HttpSecurity) {
        http.authorizeRequests()
            .mvcMatchers(HttpMethod.GET, "/**")
            .hasAuthority("haitaton-user")
            .anyRequest().authenticated()
    }

    @Bean
    fun tokenStore(): TokenStore = JwkTokenStore(urlJwk)

    @Bean
    fun securityEvaluationContextExtension(): SecurityEvaluationContextExtension = SecurityEvaluationContextExtension()

    @Bean
    fun handler(): SecurityExpressionHandler<FilterInvocation> = OAuth2WebSecurityExpressionHandler()
}