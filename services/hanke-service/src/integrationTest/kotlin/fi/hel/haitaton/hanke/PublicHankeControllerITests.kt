package fi.hel.haitaton.hanke

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import fi.hel.haitaton.hanke.domain.Hankevaihe
import fi.hel.haitaton.hanke.domain.PublicGeometriat
import fi.hel.haitaton.hanke.domain.PublicHanke
import fi.hel.haitaton.hanke.domain.PublicHankeMinimal
import fi.hel.haitaton.hanke.domain.PublicHankeYhteystieto
import fi.hel.haitaton.hanke.domain.PublicHankealue
import fi.hel.haitaton.hanke.domain.PublicHankealueMinimal
import fi.hel.haitaton.hanke.domain.TyomaaTyyppi
import fi.hel.haitaton.hanke.domain.YhteystietoTyyppi
import fi.hel.haitaton.hanke.factory.DateFactory
import fi.hel.haitaton.hanke.factory.GeometriaFactory
import fi.hel.haitaton.hanke.factory.HaittaFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.DEFAULT_HANKEKUVAUS
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.DEFAULT_HANKENIMI
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.DEFAULT_HANKETUNNUS
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.DEFAULT_HANKE_ID
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
import org.junit.jupiter.api.Nested
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

    @Nested
    inner class GetPublicHankkeetMinimal {
        @Test
        fun `returns 200 with unauthenticated user`() {
            every { hankeService.loadPublicHanke() }.returns(listOf())

            get("/public-hankkeet").andExpect(MockMvcResultMatchers.status().isOk)

            verify { hankeService.loadPublicHanke() }
        }

        @Test
        fun `returns public hankkeet with minimal data`() {
            val hankkeet =
                listOf(
                    HankeFactory.create(id = 1, nimi = "nimi").withHankealue().withYhteystiedot(),
                    HankeFactory.create(id = 2, nimi = "null").withHankealue().withYhteystiedot(),
                )
            every { hankeService.loadPublicHanke() }.returns(hankkeet)

            val response: List<PublicHankeMinimal> =
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
                PublicHankealueMinimal(
                    id = 1,
                    hankeId = hankeId,
                    nimi = "$HANKEALUE_DEFAULT_NAME 1",
                    haittaAlkuPvm = DateFactory.getStartDatetime(),
                    haittaLoppuPvm = DateFactory.getEndDatetime(),
                    geometriat = PublicGeometriat(GeometriaFactory.create(1).featureCollection),
                    tormaystarkastelu = HaittaFactory.tormaystarkasteluTulos(),
                )
            fun expectedHanke(id: Int, nimi: String) =
                PublicHankeMinimal(
                    id = id,
                    hankeTunnus = DEFAULT_HANKETUNNUS,
                    nimi = nimi,
                    alueet = listOf(expectedAlue(id)),
                )
            assertThat(response)
                .containsExactlyInAnyOrder(expectedHanke(1, "nimi"), expectedHanke(2, "null"))
            verify { hankeService.loadPublicHanke() }
        }
    }

    @Nested
    inner class GetPublicHanke {
        @Test
        fun `returns 200 with unauthenticated user`() {
            val hanke = HankeFactory.create().withHankealue().withYhteystiedot()
            every { hankeService.loadPublicHankeByHankeTunnus(DEFAULT_HANKETUNNUS) }.returns(hanke)

            get("/public-hankkeet/$DEFAULT_HANKETUNNUS")
                .andExpect(MockMvcResultMatchers.status().isOk)

            verify { hankeService.loadPublicHankeByHankeTunnus(DEFAULT_HANKETUNNUS) }
        }

        @Test
        fun `returns data when hanke exists and is public`() {
            val hanke = HankeFactory.create().withHankealue().withYhteystiedot()
            every { hankeService.loadPublicHankeByHankeTunnus(DEFAULT_HANKETUNNUS) }.returns(hanke)

            get("/public-hankkeet/$DEFAULT_HANKETUNNUS")
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(jsonPath("$.id").value(DEFAULT_HANKE_ID))
                .andExpect(jsonPath("$.nimi").value(DEFAULT_HANKENIMI))
                .andExpect(jsonPath("$.hankeTunnus").exists())
                .andExpect(jsonPath("$.alueet[0]").exists())
                .andExpect(jsonPath("$.omistajat[0]").exists())

            verify { hankeService.loadPublicHankeByHankeTunnus(DEFAULT_HANKETUNNUS) }
        }

        @Test
        fun `returns 404 when hanke does not exist or is not public`() {
            every { hankeService.loadPublicHankeByHankeTunnus(DEFAULT_HANKETUNNUS) } throws
                PublicHankeNotFoundException(DEFAULT_HANKETUNNUS)

            get("/public-hankkeet/$DEFAULT_HANKETUNNUS")
                .andExpect(MockMvcResultMatchers.status().isNotFound)

            verify { hankeService.loadPublicHankeByHankeTunnus(DEFAULT_HANKETUNNUS) }
        }

        @Test
        fun `includes complete hanke data`() {
            val hanke = HankeFactory.create().withHankealue().withYhteystiedot()
            every { hankeService.loadPublicHankeByHankeTunnus(DEFAULT_HANKETUNNUS) }.returns(hanke)

            val response: PublicHanke =
                get("/public-hankkeet/$DEFAULT_HANKETUNNUS")
                    .andExpect(MockMvcResultMatchers.status().isOk)
                    .andReturnBody()

            fun expectedAlue(hankeId: Int) =
                PublicHankealue(
                    id = 1,
                    hankeId = hankeId,
                    haittaAlkuPvm = DateFactory.getStartDatetime(),
                    haittaLoppuPvm = DateFactory.getEndDatetime(),
                    geometriat = PublicGeometriat(GeometriaFactory.create(1).featureCollection),
                    kaistaHaitta = VaikutusAutoliikenteenKaistamaariin.YKSI_KAISTA_VAHENEE,
                    kaistaPituusHaitta =
                        AutoliikenteenKaistavaikutustenPituus.PITUUS_ALLE_10_METRIA,
                    meluHaitta = Meluhaitta.SATUNNAINEN_MELUHAITTA,
                    polyHaitta = Polyhaitta.TOISTUVA_POLYHAITTA,
                    tarinaHaitta = Tarinahaitta.JATKUVA_TARINAHAITTA,
                    nimi = "$HANKEALUE_DEFAULT_NAME 1",
                    tormaystarkastelu = HaittaFactory.tormaystarkasteluTulos(),
                )
            fun expectedHanke(id: Int, nimi: String) =
                PublicHanke(
                    id = id,
                    hankeTunnus = DEFAULT_HANKETUNNUS,
                    nimi = nimi,
                    kuvaus = DEFAULT_HANKEKUVAUS,
                    alkuPvm = DateFactory.getStartDatetime(),
                    loppuPvm = DateFactory.getEndDatetime(),
                    vaihe = Hankevaihe.OHJELMOINTI,
                    tyomaaTyyppi = setOf(TyomaaTyyppi.VESI, TyomaaTyyppi.MUU),
                    omistajat = listOf(PublicHankeYhteystieto("etu1 suku1")),
                    alueet = listOf(expectedAlue(id)),
                )
            assertThat(response).isEqualTo(expectedHanke(DEFAULT_HANKE_ID, DEFAULT_HANKENIMI))
            verify { hankeService.loadPublicHankeByHankeTunnus(DEFAULT_HANKETUNNUS) }
        }

        @Test
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
            every { hankeService.loadPublicHankeByHankeTunnus(DEFAULT_HANKETUNNUS) }.returns(hanke)

            get("/public-hankkeet/$DEFAULT_HANKETUNNUS")
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(jsonPath("$.omistajat[0].organisaatioNimi").value("Yritys Oy"))
                .andExpect(jsonPath("$.omistajat[0].email").doesNotExist())
                .andExpect(jsonPath("$.omistajat[0].puhelinnumero").doesNotExist())
                .andExpect(jsonPath("$.omistajat[1].organisaatioNimi").value(null))
                .andExpect(jsonPath("$.omistajat[2].organisaatioNimi").value("Yhdistys Ry"))
                .andExpect(jsonPath("$.toteuttajat").doesNotExist())
                .andExpect(jsonPath("$.rakennuttajat").doesNotExist())
                .andExpect(jsonPath("$.muut").doesNotExist())

            verify { hankeService.loadPublicHankeByHankeTunnus(DEFAULT_HANKETUNNUS) }
        }
    }
}
