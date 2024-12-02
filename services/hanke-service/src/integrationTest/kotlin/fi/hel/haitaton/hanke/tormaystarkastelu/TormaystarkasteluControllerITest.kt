package fi.hel.haitaton.hanke.tormaystarkastelu

import assertk.assertThat
import assertk.assertions.isEqualTo
import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.andReturnBody
import fi.hel.haitaton.hanke.factory.DateFactory
import fi.hel.haitaton.hanke.factory.GeometriaFactory
import fi.hel.haitaton.hanke.factory.HankealueFactory.TORMAYSTARKASTELU_DEFAULT_AUTOLIIKENNELUOKITTELU
import fi.hel.haitaton.hanke.hankeError
import fi.hel.haitaton.hanke.test.USERNAME
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verifySequence
import java.time.ZonedDateTime
import org.geojson.FeatureCollection
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithAnonymousUser
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [TormaystarkasteluController::class])
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class TormaystarkasteluControllerITest(
    @Autowired override val mockMvc: MockMvc,
    @Autowired val laskentaService: TormaystarkasteluLaskentaService,
) : ControllerTest {

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(laskentaService)
    }

    @Nested
    inner class Calculate {
        val url = "/haittaindeksit"

        @Test
        @WithAnonymousUser
        fun `returns 401 when not authenticated`() {
            post(url).andExpect(status().isUnauthorized)

            verifySequence { laskentaService wasNot Called }
        }

        @Test
        fun `returns 400 when request has wrong CRS`() {
            val request = createRequest()
            request.geometriat.featureCollection.crs
                ?.properties
                ?.set("name", "urn:ogc:def:crs:EPSG::0000")

            post(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI1013))
        }

        @Test
        fun `returns 400 when request has no CRS`() {
            val request = createRequest()
            request.geometriat.featureCollection.crs = null

            post(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI1011))
        }

        @Test
        fun `returns 400 when request has no CRS properties`() {
            val request = createRequest()
            request.geometriat.featureCollection.crs.properties = null

            post(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI1011))
        }

        @ParameterizedTest
        @CsvSource(
            value =
                [
                    "2025-02-20T23:45:56Z,2025-02-20T23:45:55Z",
                    "2025-02-20T23:45:56Z,2025-02-19T23:47:56Z",
                ]
        )
        fun `returns 400 when start time is after end time`(
            startDate: ZonedDateTime,
            endDate: ZonedDateTime,
        ) {
            val request = createRequest(startDate, endDate)

            post(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI0003))
        }

        @ParameterizedTest
        @CsvSource(
            value =
                [
                    "2025-02-20T23:45:56Z,2025-02-21T00:12:34Z,1",
                    "2025-02-20T23:45:56Z,2025-02-20T23:45:56Z,1",
                    "2025-02-20T00:45:56Z,2025-02-20T23:12:34Z,1",
                    "2025-02-20T23:45:56Z,2025-02-21T23:45:56Z,2",
                ]
        )
        fun `calls the service with the right length in days`(
            startDate: ZonedDateTime,
            endDate: ZonedDateTime,
            days: Int,
        ) {
            val request = createRequest(startDate, endDate)
            every {
                laskentaService.calculateTormaystarkastelu(
                    any<FeatureCollection>(),
                    days,
                    VaikutusAutoliikenteenKaistamaariin.YKSI_KAISTA_VAHENEE,
                    AutoliikenteenKaistavaikutustenPituus.PITUUS_10_99_METRIA,
                )
            } returns createTulos()

            post(url, request).andExpect(status().isOk)

            verifySequence {
                laskentaService.calculateTormaystarkastelu(
                    any<FeatureCollection>(),
                    days,
                    VaikutusAutoliikenteenKaistamaariin.YKSI_KAISTA_VAHENEE,
                    AutoliikenteenKaistavaikutustenPituus.PITUUS_10_99_METRIA,
                )
            }
        }

        @Test
        fun `returns the calculation results`() {
            val request = createRequest()
            every {
                laskentaService.calculateTormaystarkastelu(
                    any<FeatureCollection>(),
                    1,
                    any(),
                    any(),
                )
            } returns createTulos()

            val result: TormaystarkasteluTulos =
                post(url, request).andExpect(status().isOk).andReturnBody()

            assertThat(result).isEqualTo(createTulos())
            verifySequence {
                laskentaService.calculateTormaystarkastelu(
                    any<FeatureCollection>(),
                    1,
                    any(),
                    any(),
                )
            }
        }

        private fun createRequest(
            haittaAlkuPvm: ZonedDateTime = DateFactory.getStartDatetime(),
            haittaLoppuPvm: ZonedDateTime = DateFactory.getEndDatetime(),
            kaistaHaitta: VaikutusAutoliikenteenKaistamaariin =
                VaikutusAutoliikenteenKaistamaariin.YKSI_KAISTA_VAHENEE,
            kaistaPituusHaitta: AutoliikenteenKaistavaikutustenPituus =
                AutoliikenteenKaistavaikutustenPituus.PITUUS_10_99_METRIA,
        ): TormaystarkasteluRequest {
            val featureCollection = GeometriaFactory.createNew().featureCollection!!

            return TormaystarkasteluRequest(
                geometriat = TormaystarkasteluRequest.Geometriat(featureCollection),
                haittaAlkuPvm = haittaAlkuPvm,
                haittaLoppuPvm = haittaLoppuPvm,
                kaistaHaitta = kaistaHaitta,
                kaistaPituusHaitta = kaistaPituusHaitta,
            )
        }

        private fun createTulos() =
            TormaystarkasteluTulos(
                TORMAYSTARKASTELU_DEFAULT_AUTOLIIKENNELUOKITTELU,
                2.4f,
                7.1f,
                5.0f,
            )
    }
}
