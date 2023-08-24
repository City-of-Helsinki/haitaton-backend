package fi.hel.haitaton.hanke.configuration

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Configure the router to consider URLs with trailing slashes equal with URLs without sai slashes.
 * E.g. `/hankkeet/` is routed to the same controller as `/hankkeet`.
 *
 * This was previously the default in Spring, and the frontend uses trailing slashes at least in
 * some calls, like the aforementioned `/hankkeet/`.
 *
 * This configuration can be removed after the URLs in the frontend have been fixed (HAI-1833).
 */
@Configuration
class WebConfiguration : WebMvcConfigurer {
    override fun configurePathMatch(configurer: PathMatchConfigurer) {
        configurer.setUseTrailingSlashMatch(true)
    }
}
