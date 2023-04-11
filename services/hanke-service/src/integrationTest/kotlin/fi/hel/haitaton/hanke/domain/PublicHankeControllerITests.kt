package fi.hel.haitaton.hanke.domain

import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.IntegrationTestResourceServerConfig
import fi.hel.haitaton.hanke.PublicHankeController
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withHankealue
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withTormaystarkasteluTulos
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withYhteystiedot
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath

@WebMvcTest(PublicHankeController::class)
@Import(IntegrationTestConfiguration::class, IntegrationTestResourceServerConfig::class)
@ActiveProfiles("itest")
class PublicHankeControllerITests(@Autowired override val mockMvc: MockMvc) : ControllerTest {

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
        every { hankeService.loadPublicHanke() }.returns(listOf())

        get("/public-hankkeet").andExpect(MockMvcResultMatchers.status().isOk)

        verify { hankeService.loadPublicHanke() }
    }

    @Test
    // Without mock user, i.e. anonymous
    fun `returns public hankkeet`() {
        val hankkeet =
            listOf(
                HankeFactory.create(id = 1, nimi = "nimi")
                    .withHankealue()
                    .withYhteystiedot()
                    .withTormaystarkasteluTulos(),
                HankeFactory.create(id = 2, nimi = "null")
                    .withHankealue()
                    .withYhteystiedot()
                    .withTormaystarkasteluTulos(),
            )
        every { hankeService.loadPublicHanke() }.returns(hankkeet)

        get("/public-hankkeet")
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(jsonPath("[0]").exists())
            .andExpect(jsonPath("[1]").exists())
            .andExpect(jsonPath("[0].id").value(1))
            .andExpect(jsonPath("[0].nimi").value("nimi"))
            .andExpect(jsonPath("[1].id").value(2))
            .andExpect(jsonPath("[1].nimi").value("null"))

        verify { hankeService.loadPublicHanke() }
    }

    @Test
    // Without mock user, i.e. anonymous
    fun `doesn't return personal information from yhteystiedot`() {
        val hankkeet =
            listOf(
                HankeFactory.create()
                    .withHankealue()
                    .withYhteystiedot()
                    .withTormaystarkasteluTulos()
            )
        every { hankeService.loadPublicHanke() }.returns(hankkeet)

        get("/public-hankkeet")
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(jsonPath("[0]").exists())
            .andExpect(jsonPath("[0].omistajat[0]").exists())
            .andExpect(jsonPath("[0].omistajat[0].organisaatioId").value(1))
            .andExpect(jsonPath("[0].omistajat[0].sukunimi").doesNotExist())
            .andExpect(jsonPath("[0].omistajat[0].etunimi").doesNotExist())
            .andExpect(jsonPath("[0].omistajat[0].email").doesNotExist())
            .andExpect(jsonPath("[0].omistajat[0].puhelinnumero").doesNotExist())
            .andExpect(jsonPath("[0].toteuttajat").doesNotExist())
            .andExpect(jsonPath("[0].rakennuttajat").doesNotExist())
            .andExpect(jsonPath("[0].muut").doesNotExist())

        verify { hankeService.loadPublicHanke() }
    }
}
