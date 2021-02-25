package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.security.AccessRules
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer

@TestConfiguration
@EnableResourceServer
@EnableGlobalMethodSecurity(prePostEnabled = true)
class IntegrationTestResourceServerConfig : ResourceServerConfigurerAdapter() {

    override fun configure(http: HttpSecurity) {
        AccessRules.configureHttpAccessRules(http)
    }

    // See a comment in https://github.com/spring-projects/spring-security-oauth/issues/385
    override fun configure(resources: ResourceServerSecurityConfigurer) {
        resources.stateless(false)
    }
}
