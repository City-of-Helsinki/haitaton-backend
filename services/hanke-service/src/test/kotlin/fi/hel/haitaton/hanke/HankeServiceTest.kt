package fi.hel.haitaton.hanke

import assertk.all
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasClass
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.messageContains
import fi.hel.haitaton.hanke.application.ApplicationService
import fi.hel.haitaton.hanke.geometria.GeometriatService
import fi.hel.haitaton.hanke.logging.AuditLogService
import fi.hel.haitaton.hanke.logging.HankeLoggingService
import fi.hel.haitaton.hanke.permissions.HankeKayttajaService
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluLaskentaService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class HankeServiceTest {

    private val hankeRepository: HankeRepository = mockk()
    private val tormaystarkasteluService: TormaystarkasteluLaskentaService = mockk()
    private val hanketunnusService: HanketunnusService = mockk()
    private val geometriatService: GeometriatService = mockk()
    private val auditLogService: AuditLogService = mockk()
    private val hankeLoggingService: HankeLoggingService = mockk()
    private val applicationService: ApplicationService = mockk()
    private val permissionService: PermissionService = mockk()
    private val hankeKayttajaService: HankeKayttajaService = mockk()

    private val hankeService =
        HankeServiceImpl(
            hankeRepository,
            tormaystarkasteluService,
            hanketunnusService,
            geometriatService,
            auditLogService,
            hankeLoggingService,
            applicationService,
            permissionService,
            hankeKayttajaService,
        )

    @Nested
    inner class GetHankeId {
        val hankeTunnus = "HAI23-1"
        val hankeId = 9984

        @Test
        fun `Returns hanke id if hanke found`() {
            every { hankeRepository.findByHankeTunnus(hankeTunnus) } returns
                HankeEntity(id = hankeId)

            val response = hankeService.getHankeId(hankeTunnus)

            assertThat(response).isNotNull().isEqualTo(hankeId)
        }

        @Test
        fun `Returns null if hanke not found`() {
            every { hankeRepository.findByHankeTunnus(hankeTunnus) } returns null

            val response = hankeService.getHankeId(hankeTunnus)

            assertThat(response).isNull()
        }
    }

    @Nested
    inner class GetHankeIdOrThrow {
        val hankeTunnus = "HAI23-1"
        val hankeId = 9984

        @Test
        fun `Returns hanke id if hanke found`() {
            every { hankeRepository.findByHankeTunnus(hankeTunnus) } returns
                HankeEntity(id = hankeId)

            val response = hankeService.getHankeIdOrThrow(hankeTunnus)

            assertThat(response).isNotNull().isEqualTo(hankeId)
        }

        @Test
        fun `Returns null if hanke not found`() {
            every { hankeRepository.findByHankeTunnus(hankeTunnus) } returns null

            assertFailure { hankeService.getHankeIdOrThrow(hankeTunnus) }
                .all {
                    hasClass(HankeNotFoundException::class)
                    messageContains(hankeTunnus)
                }
        }
    }
}
