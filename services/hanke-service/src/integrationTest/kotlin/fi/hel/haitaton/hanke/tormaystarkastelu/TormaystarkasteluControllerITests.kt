package fi.hel.haitaton.hanke.tormaystarkastelu

import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.SaveType
import fi.hel.haitaton.hanke.TZ_UTC
import fi.hel.haitaton.hanke.Vaihe
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.getCurrentTimeUTC
import io.mockk.every
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@WebMvcTest(TormaystarkasteluController::class)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("itest")
@WithMockUser("test", roles = ["haitaton-user"])
class TormaystarkasteluControllerITests(@Autowired val mockMvc: MockMvc) {

    private val mockedHankeTunnus = "HAI21-T"

    @Autowired
    lateinit var hankeService: HankeService  // faking these calls

    @Autowired
    lateinit var laskentaService: TormaystarkasteluLaskentaService

    @Test
    fun `When tormaystarkastelu is called for hanke which does not have TormaystarkasteluTulos`() {
        val hanke = Hanke(
                123,
                mockedHankeTunnus,
                true,
                "Hämeentien perusparannus ja katuvalot", "lorem ipsum dolor sit amet...",
                ZonedDateTime.of(2020, 2, 20, 23, 45, 56, 0, TZ_UTC)
                        .truncatedTo(ChronoUnit.MILLIS),
                ZonedDateTime.of(2030, 2, 20, 23, 45, 56, 0, TZ_UTC)
                        .truncatedTo(ChronoUnit.MILLIS),
                Vaihe.OHJELMOINTI,
                null,
                1,
                "Risto",
                getCurrentTimeUTC(),
                null,
                null,
                SaveType.DRAFT
        )

        // faking the service call
        every { hankeService.loadHanke(any()) }.returns(hanke)

        mockMvc.perform(
            MockMvcRequestBuilders.get("/hankkeet/$mockedHankeTunnus/tormaystarkastelu")
                .accept(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isNotFound)
    }

    @Test
    fun `When createTormaystarkastelu is called for hanke without existing TormaystarkasteluTulos`() {

        val hanke = Hanke(
            123,
            mockedHankeTunnus,
            true,
            "Hämeentien perusparannus ja katuvalot", "lorem ipsum dolor sit amet...",
            ZonedDateTime.of(2020, 2, 20, 23, 45, 56, 0, TZ_UTC)
                .truncatedTo(ChronoUnit.MILLIS),
            ZonedDateTime.of(2030, 2, 20, 23, 45, 56, 0, TZ_UTC)
                .truncatedTo(ChronoUnit.MILLIS),
            Vaihe.OHJELMOINTI,
            null,
            1,
            "Risto",
            getCurrentTimeUTC(),
            null,
            null,
            SaveType.DRAFT
        )
        // faking the service call
        every { hankeService.loadHanke(any()) }.returns(hanke)

        // faking the service call
        val tormaystulos = TormaystarkasteluTulos(mockedHankeTunnus)
        tormaystulos.perusIndeksi = 2.3f

        // adding tormaystulos to hanke
        hanke.tormaystarkasteluTulos = tormaystulos

        every { laskentaService.calculateTormaystarkastelu(any()) }.returns(tormaystulos)

        mockMvc.perform(
            MockMvcRequestBuilders.post("/hankkeet/$mockedHankeTunnus/tormaystarkastelu")
                .characterEncoding("UTF-8")
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .accept(MediaType.APPLICATION_JSON)
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(
                MockMvcResultMatchers.jsonPath("$.hankeTunnus")
                    .value(mockedHankeTunnus)
            ).andExpect(
                MockMvcResultMatchers.jsonPath("$.tormaystarkasteluTulos.perusIndeksi")
                    .value(2.3f)
            )
    }

}
