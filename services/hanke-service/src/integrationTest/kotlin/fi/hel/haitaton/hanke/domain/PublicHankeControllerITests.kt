package fi.hel.haitaton.hanke.domain

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HANKEALUE_DEFAULT_NAME
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.PublicHankeController
import fi.hel.haitaton.hanke.andReturnBody
import fi.hel.haitaton.hanke.factory.DateFactory
import fi.hel.haitaton.hanke.factory.GeometriaFactory
import fi.hel.haitaton.hanke.factory.HaittaFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withHankealue
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withYhteystiedot
import fi.hel.haitaton.hanke.factory.HankeYhteystietoFactory
import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.tormaystarkastelu.Meluhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Polyhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Tarinahaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.VaikutusAutoliikenteenKaistamaariin
import io.mockk.checkUnnecessaryStub
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
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
class PublicHankeControllerITests(@Autowired override val mockMvc: MockMvc) : ControllerTest {

    @Autowired private lateinit var hankeService: HankeService

    @BeforeEach
    fun cleanup() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
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
                HankeFactory.create(id = 1, nimi = "nimi").withHankealue().withYhteystiedot(),
                HankeFactory.create(id = 2, nimi = "null").withHankealue().withYhteystiedot(),
            )
        every { hankeService.loadPublicHanke() }.returns(hankkeet)

        val response: List<PublicHanke> =
            get("/public-hankkeet")
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(jsonPath("[0]").exists())
                .andExpect(jsonPath("[1]").exists())
                .andExpect(jsonPath("[0].id").value(1))
                .andExpect(jsonPath("[0].nimi").value("nimi"))
                .andExpect(jsonPath("[1].id").value(2))
                .andExpect(jsonPath("[1].nimi").value("null"))
                .andReturnBody()

        fun expectedAlue(hankeId: Int) =
            PublicHankealue(
                id = 1,
                hankeId = hankeId,
                haittaAlkuPvm = DateFactory.getStartDatetime(),
                haittaLoppuPvm = DateFactory.getEndDatetime(),
                geometriat = PublicGeometriat(GeometriaFactory.create(1).featureCollection),
                kaistaHaitta = VaikutusAutoliikenteenKaistamaariin.YKSI_KAISTA_VAHENEE,
                kaistaPituusHaitta = AutoliikenteenKaistavaikutustenPituus.PITUUS_ALLE_10_METRIA,
                meluHaitta = Meluhaitta.SATUNNAINEN_MELUHAITTA,
                polyHaitta = Polyhaitta.TOISTUVA_POLYHAITTA,
                tarinaHaitta = Tarinahaitta.JATKUVA_TARINAHAITTA,
                nimi = "$HANKEALUE_DEFAULT_NAME 1",
                tormaystarkastelu = HaittaFactory.tormaystarkasteluTulos(),
            )
        fun expectedHanke(id: Int, nimi: String) =
            PublicHanke(
                id = id,
                hankeTunnus = HankeFactory.defaultHankeTunnus,
                nimi = nimi,
                kuvaus = HankeFactory.defaultKuvaus,
                alkuPvm = DateFactory.getStartDatetime(),
                loppuPvm = DateFactory.getEndDatetime(),
                vaihe = Hankevaihe.OHJELMOINTI,
                tyomaaTyyppi = setOf(TyomaaTyyppi.VESI, TyomaaTyyppi.MUU),
                omistajat = listOf(PublicHankeYhteystieto("etu1 suku1")),
                alueet = listOf(expectedAlue(id)),
            )
        assertThat(response)
            .containsExactlyInAnyOrder(expectedHanke(1, "nimi"), expectedHanke(2, "null"))
        verify { hankeService.loadPublicHanke() }
    }

    @Test
    // Without mock user, i.e. anonymous
    fun `doesn't return personal information from yhteystiedot`() {
        val hanke = HankeFactory.create().withHankealue().withYhteystiedot()
        hanke.omistajat =
            mutableListOf(
                HankeYhteystietoFactory.create(
                    nimi = "Yritys Oy",
                    tyyppi = YhteystietoTyyppi.YRITYS,
                ),
                HankeYhteystietoFactory.create(
                    nimi = "Ykä Yksityishenkilö",
                    tyyppi = YhteystietoTyyppi.YKSITYISHENKILO,
                ),
                HankeYhteystietoFactory.create(
                    nimi = "Yhdistys Ry",
                    tyyppi = YhteystietoTyyppi.YHTEISO,
                ),
            )
        every { hankeService.loadPublicHanke() }.returns(listOf(hanke))

        get("/public-hankkeet")
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(jsonPath("[0].omistajat[0].organisaatioNimi").value("Yritys Oy"))
            .andExpect(jsonPath("[0].omistajat[0].email").doesNotExist())
            .andExpect(jsonPath("[0].omistajat[0].puhelinnumero").doesNotExist())
            .andExpect(jsonPath("[0].omistajat[1].organisaatioNimi").value(null))
            .andExpect(jsonPath("[0].omistajat[2].organisaatioNimi").value("Yhdistys Ry"))
            .andExpect(jsonPath("[0].toteuttajat").doesNotExist())
            .andExpect(jsonPath("[0].rakennuttajat").doesNotExist())
            .andExpect(jsonPath("[0].muut").doesNotExist())

        verify { hankeService.loadPublicHanke() }
    }
}
