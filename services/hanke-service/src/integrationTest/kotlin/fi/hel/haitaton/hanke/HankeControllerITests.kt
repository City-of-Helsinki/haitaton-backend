package fi.hel.haitaton.hanke

import assertk.assertThat
import assertk.assertions.isTrue
import fi.hel.haitaton.hanke.domain.HankeStatus
import fi.hel.haitaton.hanke.domain.SavedHankealue
import fi.hel.haitaton.hanke.domain.TyomaaTyyppi
import fi.hel.haitaton.hanke.factory.DateFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withHankealue
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withMuuYhteystieto
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withOmistaja
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withRakennuttaja
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withTormaystarkasteluTulos
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withToteuttaja
import fi.hel.haitaton.hanke.factory.HankeYhteyshenkiloFactory
import fi.hel.haitaton.hanke.geometria.Geometriat
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.test.USERNAME
import fi.hel.haitaton.hanke.tormaystarkastelu.AutoliikenteenKaistavaikutustenPituus
import fi.hel.haitaton.hanke.tormaystarkastelu.IndeksiType
import fi.hel.haitaton.hanke.tormaystarkastelu.Meluhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Polyhaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.Tarinahaitta
import fi.hel.haitaton.hanke.tormaystarkastelu.VaikutusAutoliikenteenKaistamaariin
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.verifySequence
import java.time.temporal.ChronoUnit
import org.geojson.FeatureCollection
import org.hamcrest.Matchers.containsInAnyOrder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithAnonymousUser
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private const val HANKE_TUNNUS = HankeFactory.defaultHankeTunnus
private const val BASE_URL = "/hankkeet"

/**
 * Testing the Hanke Controller through a full REST request.
 *
 * This class should test only the weblayer (both HTTP server and context to be auto-mocked).
 */
@WebMvcTest(HankeController::class)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class HankeControllerITests(@Autowired override val mockMvc: MockMvc) : ControllerTest {

    @Autowired lateinit var hankeService: HankeService
    @Autowired lateinit var permissionService: PermissionService
    @Autowired lateinit var disclosureLogService: DisclosureLogService
    @Autowired lateinit var authorizer: HankeAuthorizer

    @BeforeEach
    fun cleanup() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(permissionService, disclosureLogService, hankeService, authorizer)
    }

    @Nested
    inner class GetHankeByTunnus {
        private val url = "$BASE_URL/$HANKE_TUNNUS"

        @Test
        @WithAnonymousUser
        fun `Without authenticated user return unauthorized (401) `() {
            get(url)
                .andExpect(SecurityMockMvcResultMatchers.unauthenticated())
                .andExpect(status().isUnauthorized)
                .andExpect(hankeError(HankeError.HAI0001))
        }

        @Test
        fun `When user has no view permission returns not found`() {
            every { authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name) } throws
                HankeNotFoundException(HANKE_TUNNUS)

            get(url).andExpect(status().isNotFound)

            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
            }
        }

        @Test
        fun `When hankeTunnus is given then return Hanke with it (GET)`() {
            val hankeId = 123
            val hanke = HankeFactory.create(id = hankeId, hankeTunnus = HANKE_TUNNUS)
            every { hankeService.loadHanke(HANKE_TUNNUS) }.returns(hanke)
            every {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
            } returns true

            get(url)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.nimi").value("Hämeentien perusparannus ja katuvalot"))
                .andExpect(jsonPath("$.status").value(HankeStatus.DRAFT.name))

            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
                hankeService.loadHanke(HANKE_TUNNUS)
                disclosureLogService.saveDisclosureLogsForHanke(hanke, USERNAME)
            }
        }

        @Test
        fun `Returns tormaystarkastelutulos with the hanke if it has been calculated`() {
            val autoliikenneindeksi = 4.3f
            val pyoraliikenneindeksi = 2.1f
            val linjaautoliikenneindeksi = 1.4f
            val raitioliikenneindeksi = 3f
            val hanke =
                HankeFactory.create(hankeTunnus = HANKE_TUNNUS)
                    .withTormaystarkasteluTulos(
                        autoliikenneindeksi = autoliikenneindeksi,
                        pyoraliikenneindeksi = pyoraliikenneindeksi,
                        linjaautoliikenneindeksi = linjaautoliikenneindeksi,
                        raitioliikenneindeksi = raitioliikenneindeksi
                    )
            every { hankeService.loadHanke(HANKE_TUNNUS) }.returns(hanke)
            every {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
            } returns true

            get(url)
                .andExpect(status().isOk)
                .andExpect(
                    jsonPath("tormaystarkasteluTulos.autoliikenneindeksi")
                        .value(autoliikenneindeksi)
                )
                .andExpect(
                    jsonPath("tormaystarkasteluTulos.pyoraliikenneindeksi")
                        .value(pyoraliikenneindeksi)
                )
                .andExpect(
                    jsonPath("tormaystarkasteluTulos.linjaautoliikenneindeksi")
                        .value(linjaautoliikenneindeksi)
                )
                .andExpect(
                    jsonPath("tormaystarkasteluTulos.raitioliikenneindeksi")
                        .value(raitioliikenneindeksi)
                )
                // In this case, autoliikenneindeksi has the highest value
                .andExpect(
                    jsonPath("tormaystarkasteluTulos.liikennehaittaindeksi.indeksi")
                        .value(autoliikenneindeksi)
                )
                .andExpect(
                    jsonPath("tormaystarkasteluTulos.liikennehaittaindeksi.tyyppi")
                        .value(IndeksiType.AUTOLIIKENNEINDEKSI.name)
                )

            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
                hankeService.loadHanke(HANKE_TUNNUS)
                disclosureLogService.saveDisclosureLogsForHanke(hanke, USERNAME)
            }
        }

        @Test
        fun `Returns hankeyhteystiedot and hankeyhteyshenkilot when present`() {
            val hanke =
                HankeFactory.create(hankeTunnus = HANKE_TUNNUS)
                    .withOmistaja(1, 1, HankeYhteyshenkiloFactory.create(1))
                    .withRakennuttaja(2, 2, HankeYhteyshenkiloFactory.create(2))
                    .withToteuttaja(
                        3,
                        3,
                        HankeYhteyshenkiloFactory.create(3),
                        HankeYhteyshenkiloFactory.create(5),
                        HankeYhteyshenkiloFactory.create(6)
                    )
                    .withMuuYhteystieto(4, 4, HankeYhteyshenkiloFactory.create(4))
            every { hankeService.loadHanke(HANKE_TUNNUS) }.returns(hanke)
            every {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
            } returns true

            get(url)
                .andExpect(status().isOk)
                .andExpect(jsonPath("omistajat.length()").value(1))
                .andExpect(jsonPath("omistajat[0].yhteyshenkilot.length()").value(1))
                .andExpect(jsonPath("omistajat[0].yhteyshenkilot[0].etunimi").value("Etu1"))
                .andExpect(jsonPath("omistajat[0].yhteyshenkilot[0].id").isString)
                .andExpect(jsonPath("rakennuttajat.length()").value(1))
                .andExpect(jsonPath("rakennuttajat[0].yhteyshenkilot.length()").value(1))
                .andExpect(jsonPath("rakennuttajat[0].yhteyshenkilot[0].etunimi").value("Etu2"))
                .andExpect(jsonPath("rakennuttajat[0].yhteyshenkilot[0].id").isString)
                .andExpect(jsonPath("toteuttajat.length()").value(1))
                .andExpect(jsonPath("toteuttajat[0].yhteyshenkilot.length()").value(3))
                .andExpect(
                    jsonPath(
                        "toteuttajat[0].yhteyshenkilot[*].etunimi",
                        containsInAnyOrder("Etu3", "Etu5", "Etu6"),
                    )
                )
                .andExpect(jsonPath("muut.length()").value(1))
                .andExpect(jsonPath("muut[0].yhteyshenkilot.length()").value(1))
                .andExpect(jsonPath("muut[0].yhteyshenkilot[0].etunimi").value("Etu4"))
                .andExpect(jsonPath("muut[0].yhteyshenkilot[0].id").isString)

            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
                hankeService.loadHanke(HANKE_TUNNUS)
                disclosureLogService.saveDisclosureLogsForHanke(hanke, USERNAME)
            }
        }
    }

    @Nested
    inner class GetHankeList {
        private val url = BASE_URL

        @Test
        @WithAnonymousUser
        fun `Without authenticated user return unauthorized (401) `() {
            get(url)
                .andExpect(SecurityMockMvcResultMatchers.unauthenticated())
                .andExpect(status().isUnauthorized)
                .andExpect(hankeError(HankeError.HAI0001))
        }

        @Test
        fun `Without parameters return all Hanke data without geometry`() {
            val hankeIds = listOf(123, 444)
            val hankkeet =
                listOf(
                    HankeFactory.create(
                        id = hankeIds[0],
                    ),
                    HankeFactory.create(
                        id = hankeIds[1],
                        hankeTunnus = "hanketunnus2",
                        nimi = "Esplanadin viemäröinti",
                    )
                )
            every { hankeService.loadHankkeetByIds(hankeIds) }.returns(hankkeet)
            every { permissionService.getAllowedHankeIds(USERNAME, PermissionCode.VIEW) }
                .returns(hankeIds)

            // we check that we get the two hankeTunnus we expect
            get(url)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[2]").doesNotExist())
                .andExpect(jsonPath("$[0].hankeTunnus").value(HANKE_TUNNUS))
                .andExpect(jsonPath("$[1].hankeTunnus").value("hanketunnus2"))
                .andExpect(jsonPath("$[0].id").value(hankeIds[0]))
                .andExpect(jsonPath("$[1].id").value(hankeIds[1]))
                .andExpect(jsonPath("$[0].alueet[0].geometriat").doesNotExist())
                .andExpect(jsonPath("$[1].alueet[0].geometriat").doesNotExist())

            verifySequence {
                permissionService.getAllowedHankeIds(USERNAME, PermissionCode.VIEW)
                hankeService.loadHankkeetByIds(hankeIds)
                disclosureLogService.saveDisclosureLogsForHankkeet(hankkeet, USERNAME)
            }
        }

        @Test
        fun `When calling get with geometry=true then return all Hanke data with geometry`() {
            // faking the service call with two returned Hanke
            val hankeIds = listOf(123, 444)
            val alue1 =
                SavedHankealue(
                    hankeId = hankeIds[0],
                    geometriat = Geometriat(1, FeatureCollection(), version = 1),
                    nimi = "$HANKEALUE_DEFAULT_NAME 1",
                    tormaystarkasteluTulos = null,
                )
            val alue2 =
                SavedHankealue(
                    hankeId = hankeIds[1],
                    geometriat = Geometriat(2, FeatureCollection(), version = 1),
                    nimi = "$HANKEALUE_DEFAULT_NAME 2",
                    tormaystarkasteluTulos = null,
                )
            val hanke1 = HankeFactory.create(id = hankeIds[0], hankeTunnus = HANKE_TUNNUS)
            val hanke2 = HankeFactory.create(id = hankeIds[1], hankeTunnus = "hanketunnus2")
            hanke1.alueet = mutableListOf(alue1)
            hanke2.alueet = mutableListOf(alue2)
            val hankkeet = listOf(hanke1, hanke2)

            every { hankeService.loadHankkeetByIds(hankeIds) }.returns(hankkeet)
            every { permissionService.getAllowedHankeIds(USERNAME, PermissionCode.VIEW) }
                .returns(hankeIds)

            // we check that we get the two hankeTunnus and geometriat we expect
            get("$url?geometry=true")
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].hankeTunnus").value(HANKE_TUNNUS))
                .andExpect(jsonPath("$[1].hankeTunnus").value("hanketunnus2"))
                .andExpect(jsonPath("$[0].id").value(hankeIds[0]))
                .andExpect(jsonPath("$[1].id").value(hankeIds[1]))
                .andExpect(jsonPath("$[0].alueet[0].geometriat.id").value(1))
                .andExpect(jsonPath("$[1].alueet[0].geometriat.id").value(2))

            verifySequence {
                permissionService.getAllowedHankeIds(USERNAME, PermissionCode.VIEW)
                hankeService.loadHankkeetByIds(hankeIds)
                disclosureLogService.saveDisclosureLogsForHankkeet(hankkeet, USERNAME)
            }
        }

        @Test
        fun `Returns hankeyhteystiedot and hankeyhteyshenkilot when present`() {
            val hankeIds = listOf(122, 144)
            val hankkeet =
                hankeIds.map { id ->
                    HankeFactory.create(hankeTunnus = HANKE_TUNNUS)
                        .withOmistaja(id, id, HankeYhteyshenkiloFactory.create(id))
                }
            every { hankeService.loadHankkeetByIds(hankeIds) }.returns(hankkeet)
            every { permissionService.getAllowedHankeIds(USERNAME, PermissionCode.VIEW) }
                .returns(hankeIds)

            get(url)
                .andExpect(status().isOk)
                .andExpect(jsonPath("\$.length()").value(2))
                .andExpect(jsonPath("\$.[0].omistajat.length()").value(1))
                .andExpect(jsonPath("\$.[0].omistajat[0].yhteyshenkilot.length()").value(1))
                .andExpect(
                    jsonPath("\$.[0].omistajat[0].yhteyshenkilot[0].etunimi").value("Etu122")
                )
                .andExpect(jsonPath("\$.[0].omistajat[0].yhteyshenkilot[0].id").isString)
                .andExpect(jsonPath("\$.[1].omistajat.length()").value(1))
                .andExpect(jsonPath("\$.[1].omistajat[0].yhteyshenkilot.length()").value(1))
                .andExpect(
                    jsonPath("\$.[1].omistajat[0].yhteyshenkilot[0].etunimi").value("Etu144")
                )
                .andExpect(jsonPath("\$.[1].omistajat[0].yhteyshenkilot[0].id").isString)

            verifySequence {
                permissionService.getAllowedHankeIds(USERNAME, PermissionCode.VIEW)
                hankeService.loadHankkeetByIds(hankeIds)
                disclosureLogService.saveDisclosureLogsForHankkeet(hankkeet, USERNAME)
            }
        }
    }

    @Nested
    inner class CreateHanke {
        private val url = BASE_URL

        @Test
        @WithAnonymousUser
        fun `Without authenticated user return unauthorized (401)`() {
            post(url, HankeFactory.createRequest())
                .andExpect(SecurityMockMvcResultMatchers.unauthenticated())
                .andExpect(status().isUnauthorized)
                .andExpect(hankeError(HankeError.HAI0001))
        }

        @Test
        fun `Add Hanke and return it with newly created hankeTunnus`() {
            val request = HankeFactory.createRequest()
            val hankeId = 1
            val createdHanke =
                HankeFactory.create(
                    id = hankeId,
                    hankeTunnus = HANKE_TUNNUS,
                    version = 0,
                    createdBy = USERNAME,
                    createdAt = getCurrentTimeUTC(),
                )
            every { hankeService.createHanke(request, any()) }.returns(createdHanke)

            post(url, request)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.hankeTunnus").value(HANKE_TUNNUS))
                .andExpect(content().json(createdHanke.toJsonString()))

            verifySequence {
                hankeService.createHanke(request, any())
                disclosureLogService.saveDisclosureLogsForHanke(createdHanke, USERNAME)
            }
        }

        @Test
        fun `Returns 400 when request has missing data`() {
            val request = HankeFactory.createRequest(nimi = "")

            post(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI1002))
        }

        @Test
        fun `exception in Hanke creation causes a 500 Internal Server Error response with specific HankeError`() {
            val request = HankeFactory.createRequest()
            every { hankeService.createHanke(request, any()) } throws RuntimeException("Some error")

            post(url, request)
                .andExpect(status().isInternalServerError)
                .andExpect(hankeError(HankeError.HAI0002))

            verifySequence { hankeService.createHanke(request, any()) }
        }
    }

    @Nested
    inner class UpdateHanke {
        private val url = "$BASE_URL/$HANKE_TUNNUS"

        @Test
        @WithAnonymousUser
        fun `Without authenticated user returns unauthorized (401)`() {
            put(url, HankeFactory.create())
                .andExpect(SecurityMockMvcResultMatchers.unauthenticated())
                .andExpect(status().isUnauthorized)
                .andExpect(hankeError(HankeError.HAI0001))
        }

        @Test
        fun `Without permission returns 404`() {
            val hanke = HankeFactory.create(version = null)
            every { authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.EDIT.name) } throws
                HankeNotFoundException(HANKE_TUNNUS)

            put(url, hanke).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI1001))

            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.EDIT.name)
            }
        }

        @Test
        fun `Update Hanke with data and return it (PUT)`() {
            val hankeToBeUpdated = HankeFactory.create(version = null)
            val updatedHanke =
                hankeToBeUpdated.apply {
                    modifiedAt = getCurrentTimeUTC()
                    modifiedBy = USERNAME
                    status = HankeStatus.PUBLIC
                }
            every {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.EDIT.name)
            } returns true
            every { hankeService.updateHanke(hankeToBeUpdated.hankeTunnus, any()) }
                .returns(updatedHanke)

            put(url, hankeToBeUpdated)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.nimi").value(HankeFactory.defaultNimi))
                .andExpect(jsonPath("$.status").value(HankeStatus.PUBLIC.name))

            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.EDIT.name)
                hankeService.updateHanke(hankeToBeUpdated.hankeTunnus, any())
                disclosureLogService.saveDisclosureLogsForHanke(updatedHanke, USERNAME)
            }
        }

        @Test
        fun `accepts modification with geometries`() {
            val hankeToBeUpdated = HankeFactory.create(hankeTunnus = HANKE_TUNNUS).withHankealue()
            assertThat(hankeToBeUpdated.alueet.first().geometriat!!.hasFeatures()).isTrue()
            val modifiedHanke =
                hankeToBeUpdated.copy(
                    modifiedBy = USERNAME,
                    modifiedAt = DateFactory.getEndDatetime()
                )

            every {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.EDIT.name)
            } returns true
            every { hankeService.updateHanke(hankeToBeUpdated.hankeTunnus, any()) } returns
                modifiedHanke

            put(url, hankeToBeUpdated).andExpect(status().isOk)

            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.EDIT.name)
                hankeService.updateHanke(hankeToBeUpdated.hankeTunnus, any())
                disclosureLogService.saveDisclosureLogsForHanke(modifiedHanke, USERNAME)
            }
        }

        @Test
        fun `test tyomaa and haitat-fields roundtrip`() {
            val hankeToBeUpdated = HankeFactory.create(hankeTunnus = HANKE_TUNNUS)
            hankeToBeUpdated.tyomaaKatuosoite = "Testikatu 1"
            hankeToBeUpdated.tyomaaTyyppi.add(TyomaaTyyppi.VESI)
            hankeToBeUpdated.tyomaaTyyppi.add(TyomaaTyyppi.KAASUJOHTO)
            val alue =
                SavedHankealue(
                    nimi = "$HANKEALUE_DEFAULT_NAME 1",
                    haittaAlkuPvm = DateFactory.getStartDatetime(),
                    haittaLoppuPvm = DateFactory.getEndDatetime(),
                    kaistaHaitta =
                        VaikutusAutoliikenteenKaistamaariin.VAHENTAA_KAISTAN_YHDELLA_AJOSUUNNALLA,
                    kaistaPituusHaitta =
                        AutoliikenteenKaistavaikutustenPituus.PITUUS_100_499_METRIA,
                    meluHaitta = Meluhaitta.SATUNNAINEN_MELUHAITTA,
                    polyHaitta = Polyhaitta.TOISTUVA_POLYHAITTA,
                    tarinaHaitta = Tarinahaitta.JATKUVA_TARINAHAITTA,
                    tormaystarkasteluTulos = null,
                )
            hankeToBeUpdated.alueet.add(alue)
            // Prepare the expected result/return
            // Note, "pvm" values should have become truncated to begin of the day
            val expectedDateAlku =
                DateFactory.getStartDatetime()
                    .truncatedTo(ChronoUnit.DAYS) // nextyear.2.20 00:00:00Z
            val expectedDateLoppu =
                DateFactory.getEndDatetime().truncatedTo(ChronoUnit.DAYS) // nextyear.2.21 00:00:00Z
            val expectedHanke =
                hankeToBeUpdated.apply {
                    modifiedBy = USERNAME
                    modifiedAt = getCurrentTimeUTC()
                }
            expectedHanke.alueet[0].haittaAlkuPvm = expectedDateAlku
            expectedHanke.alueet[0].haittaLoppuPvm = expectedDateLoppu
            val expectedContent = expectedHanke.toJsonString()
            every {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.EDIT.name)
            } returns true
            every { hankeService.updateHanke(hankeToBeUpdated.hankeTunnus, any()) } returns
                expectedHanke

            put(url, hankeToBeUpdated)
                .andExpect(status().isOk)
                .andExpect(content().json(expectedContent))
                // These might be redundant, but at least it is clear what we're checking here:
                .andExpect(jsonPath("$.tyomaaKatuosoite").value("Testikatu 1"))
                .andExpect(
                    jsonPath("$.alueet[0].kaistaHaitta")
                        .value(
                            VaikutusAutoliikenteenKaistamaariin
                                .VAHENTAA_KAISTAN_YHDELLA_AJOSUUNNALLA
                                .name
                        )
                ) // Note, here as string, not the enum.

            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.EDIT.name)
                hankeService.updateHanke(hankeToBeUpdated.hankeTunnus, any())
                disclosureLogService.saveDisclosureLogsForHanke(expectedHanke, USERNAME)
            }
        }
    }

    @Nested
    inner class DeleteHanke {
        private val url = "$BASE_URL/$HANKE_TUNNUS"

        @Test
        @WithAnonymousUser
        fun `Without authenticated user returns unauthorized (401)`() {
            delete(url)
                .andExpect(SecurityMockMvcResultMatchers.unauthenticated())
                .andExpect(status().isUnauthorized)
                .andExpect(hankeError(HankeError.HAI0001))
        }

        @Test
        fun `When user has permission and hanke exists should call delete and return no content`() {
            every { authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.DELETE.name) }
                .returns(true)
            justRun { hankeService.deleteHanke(HANKE_TUNNUS, USERNAME) }

            delete(url).andExpect(status().isNoContent)

            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.DELETE.name)
                hankeService.deleteHanke(HANKE_TUNNUS, USERNAME)
            }
        }

        @Test
        fun `When hanke does not exist should not call delete returns not found`() {
            every {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.DELETE.name)
            } throws HankeNotFoundException(HANKE_TUNNUS)

            delete(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI1001))

            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.DELETE.name)
            }
        }
    }
}
