package fi.hel.haitaton.hanke.domain

import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.IntegrationTestResourceServerConfig
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.withHaitta
import fi.hel.haitaton.hanke.factory.HankeFactory.withYhteystiedot
import fi.hel.haitaton.hanke.geometria.HankeGeometriatService
import fi.hel.haitaton.hanke.tormaystarkastelu.TormaystarkasteluTulos
import io.mockk.clearAllMocks
import io.mockk.every
import mu.KotlinLogging
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath

private val logger = KotlinLogging.logger {}

@WebMvcTest(PublicHankeController::class)
@Import(IntegrationTestConfiguration::class, IntegrationTestResourceServerConfig::class)
@ActiveProfiles("itest")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublicHankeControllerITests(@Autowired val mockMvc: MockMvc) {

    @Autowired private lateinit var hankeService: HankeService

    @Autowired private lateinit var hankeGeometriatService: HankeGeometriatService

    private val testHankeTunnus = "HAI21-TEST-1"

    @AfterEach
    fun cleanup() {
        clearAllMocks()
    }

    @Test
    // Without mock user, i.e. anonymous
    fun `status ok with unauthenticated user`() {
        performGetHankkeet(listOf()).andExpect(MockMvcResultMatchers.status().isOk)
    }

    @Test
    // Without mock user, i.e. anonymous
    fun `only returns hankkeet with tormaystarkasteluTulos`() {
        performGetHankkeet(
                listOf(
                    getTestHanke(123, testHankeTunnus, getTestTormaystarkasteluTulos()),
                    getTestHanke(444, "HAI-TEST-2", null)
                )
            )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(jsonPath("[0]").exists())
            .andExpect(jsonPath("[1]").doesNotExist())
            .andExpect(jsonPath("[0].id").value(123))
    }

    @Test
    // Without mock user, i.e. anonymous
    fun `doesn't return personal information from yhteystiedot`() {
        performGetHankkeet(
                listOf(getTestHanke(123, testHankeTunnus, getTestTormaystarkasteluTulos()))
            )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(jsonPath("[0]").exists())
            .andExpect(jsonPath("[0].omistajat[0]").exists())
            .andExpect(jsonPath("[0].omistajat[0].organisaatioId").value(1))
            .andExpect(jsonPath("[0].omistajat[0].sukunimi").doesNotExist())
            .andExpect(jsonPath("[0].omistajat[0].etunimi").doesNotExist())
            .andExpect(jsonPath("[0].omistajat[0].email").doesNotExist())
            .andExpect(jsonPath("[0].omistajat[0].puhelinnumero").doesNotExist())
            .andExpect(jsonPath("[0].toteuttajat").doesNotExist())
            .andExpect(jsonPath("[0].arvioijat").doesNotExist())
    }

    private fun performGetHankkeet(hankkeet: List<Hanke>): ResultActions {
        every { hankeService.loadAllHanke() }.returns(hankkeet)

        every { hankeGeometriatService.loadGeometriat(any()) }.returns(null)

        return mockMvc.perform(
            MockMvcRequestBuilders.get("/public-hankkeet").accept(MediaType.APPLICATION_JSON)
        )
    }

    private fun getTestHanke(
        id: Int?,
        tunnus: String?,
        tormaystarkasteluTulos: TormaystarkasteluTulos?
    ): Hanke {
        val hanke = HankeFactory.create(id, hankeTunnus = tunnus).withHaitta().withYhteystiedot()
        hanke.tormaystarkasteluTulos = tormaystarkasteluTulos
        return hanke
    }

    private fun getTestTormaystarkasteluTulos(): TormaystarkasteluTulos {
        return TormaystarkasteluTulos(1f, 1f, 1f)
    }
}
