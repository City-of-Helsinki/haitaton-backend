package fi.hel.haitaton.hanke

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
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

    companion object {
        const val BASE_URL_PATH = "/public-hankkeet"
    }

    @Nested
    inner class GetPublicHankkeet {
        @Test
        fun `returns 200 with unauthenticated user`() {
            every { hankeService.loadPublicHanke() }.returns(listOf())

            get(BASE_URL_PATH).andExpect(MockMvcResultMatchers.status().isOk)

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
                get(BASE_URL_PATH)
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

        @Test
        fun `calls the right function for bounded area`() {
            val startDate = DateFactory.getStartDatetime().toLocalDate()
            val endDate = DateFactory.getEndDatetime().toLocalDate()
            val hankkeet =
                listOf(
                    HankeFactory.create(id = 1, nimi = "nimi").withHankealue().withYhteystiedot()
                )
            every {
                    hankeService.loadPublicHankeWithinBounds(startDate, endDate, 0.0, 0.0, 1.0, 1.0)
                }
                .returns(hankkeet)

            get(
                    "/public-hankkeet?startDate=${startDate}&endDate=${endDate}&minX=0.0&minY=0.0&maxX=1.0&maxY=1.0"
                )
                .andExpect(MockMvcResultMatchers.status().isOk)

            verify {
                hankeService.loadPublicHankeWithinBounds(startDate, endDate, 0.0, 0.0, 1.0, 1.0)
            }
        }
    }

    @Nested
    inner class GetPublicHanke {
        @Test
        fun `returns 200 with unauthenticated user`() {
            val hanke = HankeFactory.create().withHankealue().withYhteystiedot()
            every { hankeService.loadPublicHankeByHankeTunnus(DEFAULT_HANKETUNNUS) }.returns(hanke)

            get("$BASE_URL_PATH/$DEFAULT_HANKETUNNUS")
                .andExpect(MockMvcResultMatchers.status().isOk)

            verify { hankeService.loadPublicHankeByHankeTunnus(DEFAULT_HANKETUNNUS) }
        }

        @Test
        fun `returns data when hanke exists and is public`() {
            val hanke = HankeFactory.create().withHankealue().withYhteystiedot()
            every { hankeService.loadPublicHankeByHankeTunnus(DEFAULT_HANKETUNNUS) }.returns(hanke)

            get("$BASE_URL_PATH/$DEFAULT_HANKETUNNUS")
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

            get("$BASE_URL_PATH/$DEFAULT_HANKETUNNUS")
                .andExpect(MockMvcResultMatchers.status().isNotFound)

            verify { hankeService.loadPublicHankeByHankeTunnus(DEFAULT_HANKETUNNUS) }
        }

        @Test
        fun `includes complete hanke data`() {
            val hanke = HankeFactory.create().withHankealue().withYhteystiedot()
            every { hankeService.loadPublicHankeByHankeTunnus(DEFAULT_HANKETUNNUS) }.returns(hanke)

            val response: PublicHanke =
                get("$BASE_URL_PATH/$DEFAULT_HANKETUNNUS")
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

            get("$BASE_URL_PATH/$DEFAULT_HANKETUNNUS")
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

    @Nested
    inner class GetAllByDateAndGridCells {

        @Test
        fun `returns 200 with unauthenticated user`() {
            val request =
                PublicHankeGridCellRequest(
                    startDate = DateFactory.getStartDatetime().toLocalDate(),
                    endDate = DateFactory.getEndDatetime().toLocalDate(),
                    cells = listOf(GridCell(1, 1)),
                )
            every {
                hankeService.loadPublicHankeInGridCells(
                    request.startDate,
                    request.endDate,
                    request.cells,
                )
            } returns listOf()

            post("${BASE_URL_PATH}/grid", request).andExpect(MockMvcResultMatchers.status().isOk)

            verify {
                hankeService.loadPublicHankeInGridCells(
                    request.startDate,
                    request.endDate,
                    request.cells,
                )
            }
        }

        @Test
        fun `returns public hankkeet for single grid cell`() {
            val hankeName = "Grid Cell Hanke"
            val startDate = DateFactory.getStartDatetime().toLocalDate()
            val endDate = DateFactory.getEndDatetime().toLocalDate()
            val gridCell = GridCell(1, 2)
            val hankkeet =
                listOf(
                    HankeFactory.create(id = 1, nimi = hankeName).withHankealue().withYhteystiedot()
                )
            val request =
                PublicHankeGridCellRequest(
                    startDate = startDate,
                    endDate = endDate,
                    cells = listOf(gridCell),
                )
            every {
                hankeService.loadPublicHankeInGridCells(startDate, endDate, listOf(gridCell))
            } returns hankkeet

            val response: List<PublicHankeMinimal> =
                post("${BASE_URL_PATH}/grid", request)
                    .andExpect(MockMvcResultMatchers.status().isOk)
                    .andExpect(jsonPath("[0]").exists())
                    .andExpect(jsonPath("[0].id").value(1))
                    .andExpect(jsonPath("[0].nimi").value(hankeName))
                    .andExpect(jsonPath("[0].alueet").exists())
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
            val expectedHanke =
                PublicHankeMinimal(
                    id = 1,
                    hankeTunnus = DEFAULT_HANKETUNNUS,
                    nimi = hankeName,
                    alueet = listOf(expectedAlue(1)),
                )
            assertThat(response).containsExactlyInAnyOrder(expectedHanke)
            verify { hankeService.loadPublicHankeInGridCells(startDate, endDate, listOf(gridCell)) }
        }

        @Test
        fun `returns public hankkeet for multiple grid cells`() {
            val startDate = DateFactory.getStartDatetime().toLocalDate()
            val endDate = DateFactory.getEndDatetime().toLocalDate()
            val gridCells = listOf(GridCell(1, 1), GridCell(1, 2), GridCell(2, 1))
            val hankkeet =
                listOf(
                    HankeFactory.create(id = 1, nimi = "Hanke 1")
                        .withHankealue()
                        .withYhteystiedot(),
                    HankeFactory.create(id = 2, nimi = "Hanke 2").withHankealue().withYhteystiedot(),
                )
            val request =
                PublicHankeGridCellRequest(
                    startDate = startDate,
                    endDate = endDate,
                    cells = gridCells,
                )
            every { hankeService.loadPublicHankeInGridCells(startDate, endDate, gridCells) } returns
                hankkeet

            val response: List<PublicHankeMinimal> =
                post("${BASE_URL_PATH}/grid", request)
                    .andExpect(MockMvcResultMatchers.status().isOk)
                    .andExpect(jsonPath("[0]").exists())
                    .andExpect(jsonPath("[1]").exists())
                    .andExpect(jsonPath("$").isArray)
                    .andReturnBody()

            assertThat(response).hasSize(2)
            verify { hankeService.loadPublicHankeInGridCells(startDate, endDate, gridCells) }
        }

        @Test
        fun `returns empty list when no hankkeet found in grid cells`() {
            val startDate = DateFactory.getStartDatetime().toLocalDate()
            val endDate = DateFactory.getEndDatetime().toLocalDate()
            val gridCell = GridCell(99, 99)
            val request =
                PublicHankeGridCellRequest(
                    startDate = startDate,
                    endDate = endDate,
                    cells = listOf(gridCell),
                )
            every {
                hankeService.loadPublicHankeInGridCells(startDate, endDate, listOf(gridCell))
            } returns emptyList()

            val response: List<PublicHankeMinimal> =
                post("${BASE_URL_PATH}/grid", request)
                    .andExpect(MockMvcResultMatchers.status().isOk)
                    .andExpect(jsonPath("$").isEmpty)
                    .andReturnBody()

            assertThat(response).isEmpty()
            verify { hankeService.loadPublicHankeInGridCells(startDate, endDate, listOf(gridCell)) }
        }

        @Test
        fun `handles request with empty cells list`() {
            val startDate = DateFactory.getStartDatetime().toLocalDate()
            val endDate = DateFactory.getEndDatetime().toLocalDate()
            val emptyCells: List<GridCell> = emptyList()
            val request =
                PublicHankeGridCellRequest(
                    startDate = startDate,
                    endDate = endDate,
                    cells = emptyCells,
                )
            every {
                hankeService.loadPublicHankeInGridCells(startDate, endDate, emptyCells)
            } returns emptyList()

            post("${BASE_URL_PATH}/grid", request).andExpect(MockMvcResultMatchers.status().isOk)

            verify { hankeService.loadPublicHankeInGridCells(startDate, endDate, emptyCells) }
        }

        @Test
        fun `validates date range parameters`() {
            val startDate = DateFactory.getStartDatetime().toLocalDate().plusDays(10)
            val endDate = DateFactory.getStartDatetime().toLocalDate() // end before start
            val gridCell = GridCell(1, 1)
            val request =
                PublicHankeGridCellRequest(
                    startDate = startDate,
                    endDate = endDate,
                    cells = listOf(gridCell),
                )
            every {
                hankeService.loadPublicHankeInGridCells(startDate, endDate, listOf(gridCell))
            } returns emptyList()

            post("${BASE_URL_PATH}/grid", request).andExpect(MockMvcResultMatchers.status().isOk)

            // Service should handle invalid date ranges
            verify { hankeService.loadPublicHankeInGridCells(startDate, endDate, listOf(gridCell)) }
        }

        @Test
        fun `returns 400 for grid cells with invalid coordinates`() {
            val startDate = DateFactory.getStartDatetime().toLocalDate()
            val endDate = DateFactory.getEndDatetime().toLocalDate()
            val gridCell = GridCell(-1, -2)
            val request =
                PublicHankeGridCellRequest(
                    startDate = startDate,
                    endDate = endDate,
                    cells = listOf(gridCell),
                )
            every {
                hankeService.loadPublicHankeInGridCells(startDate, endDate, listOf(gridCell))
            } throws
                InvalidGridCellException(
                    "Grid cell coordinates must be non-negative. Got: (-1, -2)"
                )

            post("${BASE_URL_PATH}/grid", request)
                .andExpect(MockMvcResultMatchers.status().isBadRequest)
                .andExpect(jsonPath("$.errorCode").value("HAI0003"))
                .andExpect(jsonPath("$.errorMessage").value("Invalid data"))

            verify { hankeService.loadPublicHankeInGridCells(startDate, endDate, listOf(gridCell)) }
        }
    }

    @Nested
    inner class GetGridMetadata {

        @Test
        fun `returns grid metadata for client-side caching`() {
            val result =
                get("${BASE_URL_PATH}/grid/metadata")
                    .andExpect(MockMvcResultMatchers.status().isOk)
                    .andReturn()

            val gridMetadata =
                OBJECT_MAPPER.readValue(result.response.contentAsString, GridMetadata::class.java)

            assertThat(gridMetadata.cellSizeMeters).isEqualTo(1000)
            assertThat(gridMetadata.originX).isEqualTo(25486422.0)
            assertThat(gridMetadata.originY).isEqualTo(6643836.0)
            assertThat(gridMetadata.maxX).isEqualTo(25515423.0)
            assertThat(gridMetadata.maxY).isEqualTo(6687837.0)
        }
    }
}
