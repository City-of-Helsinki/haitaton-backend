package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.application.ApplicationAuthorizer
import fi.hel.haitaton.hanke.application.ApplicationService
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentService
import fi.hel.haitaton.hanke.attachment.hanke.HankeAttachmentAuthorizer
import fi.hel.haitaton.hanke.attachment.hanke.HankeAttachmentService
import fi.hel.haitaton.hanke.configuration.FeatureFlags
import fi.hel.haitaton.hanke.configuration.FeatureService
import fi.hel.haitaton.hanke.gdpr.GdprProperties
import fi.hel.haitaton.hanke.gdpr.GdprService
import fi.hel.haitaton.hanke.geometria.GeometriatDao
import fi.hel.haitaton.hanke.geometria.GeometriatService
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.permissions.HankeKayttajaAuthorizer
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.security.AccessRules
import fi.hel.haitaton.hanke.testdata.TestDataService
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluLaskentaService
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTormaysService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.sentry.Sentry
import io.sentry.protocol.SentryId
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@TestConfiguration
@EnableConfigurationProperties(GdprProperties::class, FeatureFlags::class)
@EnableMethodSecurity(prePostEnabled = true)
class IntegrationTestConfiguration {

    @Bean fun jdbcOperations(): JdbcOperations = mockk()

    @Bean fun hankeRepository(): HankeRepository = mockk()

    @Bean fun auditLogRepository(): AuditLogRepository = mockk()

    @Bean fun hanketunnusService(): HanketunnusService = mockk()

    @Bean fun hankeService(): HankeService = mockk()

    @Bean fun applicationService(): ApplicationService = mockk()

    @Bean fun gdprService(): GdprService = mockk(relaxUnitFun = true)

    @Bean fun testDataService(): TestDataService = mockk(relaxUnitFun = true)

    @Bean fun permissionService(): PermissionService = mockk()

    @Bean fun geometriatDao(jdbcOperations: JdbcOperations): GeometriatDao = mockk()

    @Bean
    fun geometriatService(service: HankeService, geometriatDao: GeometriatDao): GeometriatService =
        mockk()

    @Bean
    fun tormaysService(jdbcOperations: JdbcOperations): TormaystarkasteluTormaysService = mockk()

    @Bean fun tormaystarkasteluLaskentaService(): TormaystarkasteluLaskentaService = mockk()

    @Bean fun disclosureLogService(): DisclosureLogService = mockk(relaxUnitFun = true)

    @Bean fun hankeAttachmentService(): HankeAttachmentService = mockk()

    @Bean fun applicationAttachmentService(): ApplicationAttachmentService = mockk()

    @Bean fun hankeKayttajaService(): HankeKayttajaService = mockk()

    @Bean fun hankeKayttajaAuthorizer(): HankeKayttajaAuthorizer = mockk(relaxUnitFun = true)

    @Bean fun hankeAuthorizer(): HankeAuthorizer = mockk(relaxUnitFun = true)

    @Bean fun applicationAuthorizer(): ApplicationAuthorizer = mockk(relaxUnitFun = true)

    @Bean fun hankeAttachmentAuthorizer(): HankeAttachmentAuthorizer = mockk(relaxUnitFun = true)

    @Bean fun featureService(featureFlags: FeatureFlags) = FeatureService(featureFlags)

    @EventListener
    fun onApplicationEvent(event: ContextRefreshedEvent) {
        // disable Sentry by mocking it
        mockkStatic(Sentry::class)
        every { Sentry.captureException(any()) } returns SentryId()
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        AccessRules.configureHttpAccessRules(http)
        return http.build()
    }
}
