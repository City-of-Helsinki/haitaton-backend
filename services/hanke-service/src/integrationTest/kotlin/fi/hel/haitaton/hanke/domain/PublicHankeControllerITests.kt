package fi.hel.haitaton.hanke.domain

import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.IntegrationTestResourceServerConfig
import fi.hel.haitaton.hanke.PublicHankeController
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.withHankealue
import fi.hel.haitaton.hanke.factory.HankeFactory.withTormaystarkasteluTulos
import fi.hel.haitaton.hanke.factory.HankeFactory.withYhteystiedot
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
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

@WebMvcTest(PublicHankeController::class)
@Import(IntegrationTestConfiguration::class, IntegrationTestResourceServerConfig::class)
@ActiveProfiles("itest")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublicHankeControllerITests(@Autowired val mockMvc: MockMvc) {

    @Autowired private lateinit var hankeService: HankeService

    @BeforeEach
    fun cleanup() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        confirmVerified(hankeService)
    }

    @Test
    // Without mock user, i.e. anonymous
    fun `status ok with unauthenticated user`() {
        performGetHankkeet(listOf()).andExpect(MockMvcResultMatchers.status().isOk)

        verify { hankeService.loadAllHanke() }
    }

    @Test
    // Without mock user, i.e. anonymous
    fun `only returns hankkeet with mandatory fields filled`() {
        performGetHankkeet(
                listOf(
                    HankeFactory.create(id = 1, nimi = null)
                        .withHankealue()
                        .withYhteystiedot()
                        .withTormaystarkasteluTulos(),
                    HankeFactory.create(id = 2, nimi = "null")
                        .withHankealue()
                        .withYhteystiedot()
                        .withTormaystarkasteluTulos(),
                )
            )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(jsonPath("[0]").exists())
            .andExpect(jsonPath("[1]").doesNotExist())
            .andExpect(jsonPath("[0].id").value(2))
            .andExpect(jsonPath("[0].nimi").value("null"))

        verify { hankeService.loadAllHanke() }
    }

    @Test
    // Without mock user, i.e. anonymous
    fun `only returns hankkeet with tormaystarkasteluTulos`() {
        performGetHankkeet(
                listOf(
                    HankeFactory.create()
                        .withHankealue()
                        .withYhteystiedot()
                        .withTormaystarkasteluTulos(),
                    HankeFactory.create(id = 444, hankeTunnus = "HAI-TEST-2")
                        .withHankealue()
                        .withYhteystiedot()
                )
            )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(jsonPath("[0]").exists())
            .andExpect(jsonPath("[1]").doesNotExist())
            .andExpect(jsonPath("[0].id").value(HankeFactory.defaultId))

        verify { hankeService.loadAllHanke() }
    }

    @Test
    // Without mock user, i.e. anonymous
    fun `doesn't return personal information from yhteystiedot`() {
        performGetHankkeet(
                listOf(
                    HankeFactory.create()
                        .withHankealue()
                        .withYhteystiedot()
                        .withTormaystarkasteluTulos()
                )
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

        verify { hankeService.loadAllHanke() }
    }

    private fun performGetHankkeet(hankkeet: List<Hanke>): ResultActions {
        every { hankeService.loadAllHanke() }.returns(hankkeet)

        return mockMvc.perform(
            MockMvcRequestBuilders.get("/public-hankkeet").accept(MediaType.APPLICATION_JSON)
        )
    }
}
