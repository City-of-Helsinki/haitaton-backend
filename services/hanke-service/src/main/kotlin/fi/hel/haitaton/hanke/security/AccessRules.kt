package fi.hel.haitaton.hanke.security

import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity

class AccessRules {
    companion object {
        fun configureHttpAccessRules(http: HttpSecurity) {
            http.anonymous().and()
                    .authorizeRequests()
                    .mvcMatchers(HttpMethod.GET, "/organisaatiot").permitAll()
                    .mvcMatchers(HttpMethod.POST, "/hankkeet", "/hankkeet/**").hasRole("haitaton-user")
                    .mvcMatchers(HttpMethod.GET, "/hankkeet", "/hankkeet/**").hasRole("haitaton-user")
                    .mvcMatchers(HttpMethod.PUT, "/hankkeet/**").hasRole("haitaton-user")
        }
    }
}
