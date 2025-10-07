package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentMetadataService
import fi.hel.haitaton.hanke.attachment.application.ApplicationAttachmentService
import fi.hel.haitaton.hanke.attachment.hanke.HankeAttachmentAuthorizer
import fi.hel.haitaton.hanke.attachment.hanke.HankeAttachmentMetadataService
import fi.hel.haitaton.hanke.attachment.hanke.HankeAttachmentService
import fi.hel.haitaton.hanke.attachment.muutosilmoitus.MuutosilmoitusAttachmentService
import fi.hel.haitaton.hanke.attachment.taydennys.TaydennysAttachmentService
import fi.hel.haitaton.hanke.banners.BannerService
import fi.hel.haitaton.hanke.configuration.FeatureFlags
import fi.hel.haitaton.hanke.configuration.FeatureService
import fi.hel.haitaton.hanke.gdpr.GdprProperties
import fi.hel.haitaton.hanke.gdpr.GdprService
import fi.hel.haitaton.hanke.geometria.GeometriatDao
import fi.hel.haitaton.hanke.geometria.GeometriatService
import fi.hel.haitaton.hanke.hakemus.HakemusAuthorizer
import fi.hel.haitaton.hanke.hakemus.HakemusService
import fi.hel.haitaton.hanke.logging.AuditLogRepository
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.logging.DisclosureLoggingAspect
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusAuthorizer
import fi.hel.haitaton.hanke.muutosilmoitus.MuutosilmoitusService
import fi.hel.haitaton.hanke.paatos.PaatosAuthorizer
import fi.hel.haitaton.hanke.paatos.PaatosService
import fi.hel.haitaton.hanke.permissions.HankeKayttajaAuthorizer
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.permissions.HankekayttajaDeleteService
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.profiili.ProfiiliClient
import fi.hel.haitaton.hanke.profiili.ProfiiliService
import fi.hel.haitaton.hanke.security.AccessRules
import fi.hel.haitaton.hanke.security.LogoutService
import fi.hel.haitaton.hanke.security.UserSessionRepository
import fi.hel.haitaton.hanke.security.UserSessionService
import fi.hel.haitaton.hanke.taydennys.TaydennysAuthorizer
import fi.hel.haitaton.hanke.taydennys.TaydennysService
import fi.hel.haitaton.hanke.testdata.TestDataService
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluLaskentaService
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTormaysService
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.sentry.Sentry
import io.sentry.protocol.SentryId
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.cache.CacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcOperations
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain

@TestConfiguration
@EnableConfigurationProperties(
    GdprProperties::class,
    FeatureFlags::class,
    HankeMapGridProperties::class,
)
@EnableMethodSecurity(prePostEnabled = true)
@Import(value = [AopAutoConfiguration::class, DisclosureLoggingAspect::class])
class IntegrationTestConfiguration {
    @Bean fun applicationAttachmentMetadataService(): ApplicationAttachmentMetadataService = mockk()

    @Bean fun applicationAttachmentService(): ApplicationAttachmentService = mockk()

    @Bean fun auditLogRepository(): AuditLogRepository = mockk()

    @Bean fun bannerService(): BannerService = mockk()

    @Bean fun cacheManager(): CacheManager = mockk(relaxed = true)

    @Bean fun disclosureLogService(): DisclosureLogService = mockk(relaxUnitFun = true)

    @Bean fun featureService(featureFlags: FeatureFlags) = FeatureService(featureFlags)

    @Bean fun gdprService(): GdprService = mockk(relaxUnitFun = true)

    @Bean fun geometriatDao(): GeometriatDao = mockk()

    @Bean fun geometriatService(): GeometriatService = mockk()

    @Bean fun hakemusAuthorizer(): HakemusAuthorizer = mockk(relaxUnitFun = true)

    @Bean fun hakemusService(): HakemusService = mockk()

    @Bean fun hankeAttachmentAuthorizer(): HankeAttachmentAuthorizer = mockk(relaxUnitFun = true)

    @Bean fun hankeAttachmentMetadataService(): HankeAttachmentMetadataService = mockk()

    @Bean fun hankeAttachmentService(): HankeAttachmentService = mockk()

    @Bean fun hankeAuthorizer(): HankeAuthorizer = mockk(relaxUnitFun = true)

    @Bean fun hankeKayttajaAuthorizer(): HankeKayttajaAuthorizer = mockk(relaxUnitFun = true)

    @Bean fun hankekayttajaDeleteService(): HankekayttajaDeleteService = mockk()

    @Bean fun hankeKayttajaService(): HankeKayttajaService = mockk()

    @Bean fun hankeRepository(): HankeRepository = mockk()

    @Bean fun hankeService(): HankeService = mockk()

    @Bean fun hanketunnusService(): HanketunnusService = mockk()

    @Bean fun jdbcOperations(): JdbcOperations = mockk()

    @Bean fun logoutService(): LogoutService = mockk()

    @Bean fun muutosilmoitusAttachmentService(): MuutosilmoitusAttachmentService = mockk()

    @Bean fun muutosilmoitusAuthorizer(): MuutosilmoitusAuthorizer = mockk()

    @Bean fun muutosilmoitusService(): MuutosilmoitusService = mockk()

    @Bean fun paatosAuthorizer(): PaatosAuthorizer = mockk()

    @Bean fun paatosService(): PaatosService = mockk()

    @Bean fun permissionService(): PermissionService = mockk()

    @Bean fun profiiliClient(): ProfiiliClient = mockk()

    @Bean fun profiiliService(): ProfiiliService = mockk()

    @Bean fun taydennysAttachmentService(): TaydennysAttachmentService = mockk()

    @Bean fun taydennysAuthorizer(): TaydennysAuthorizer = mockk()

    @Bean fun taydennysService(): TaydennysService = mockk()

    @Bean fun testDataService(): TestDataService = mockk(relaxUnitFun = true)

    @Bean fun tormaysService(): TormaystarkasteluTormaysService = mockk()

    @Bean fun tormaystarkasteluLaskentaService(): TormaystarkasteluLaskentaService = mockk()

    @Bean fun userSessionRepository(): UserSessionRepository = mockk()

    @Bean fun userSessionService(): UserSessionService = mockk()

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
