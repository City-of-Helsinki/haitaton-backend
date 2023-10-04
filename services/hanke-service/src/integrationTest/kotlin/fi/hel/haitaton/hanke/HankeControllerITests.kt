package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.application.ApplicationsResponse
import fi.hel.haitaton.hanke.domain.Hankealue
import fi.hel.haitaton.hanke.factory.AlluDataFactory
import fi.hel.haitaton.hanke.factory.DateFactory
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withGeneratedOmistaja
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withTormaystarkasteluTulos
import fi.hel.haitaton.hanke.factory.HankeFactory.Companion.withYhteystiedot
import fi.hel.haitaton.hanke.geometria.Geometriat
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionService
import fi.hel.haitaton.hanke.tormaystarkastelu.IndeksiType
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.verifySequence
import java.time.temporal.ChronoUnit
import org.geojson.FeatureCollection
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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

private const val USERNAME = "test"
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
                disclosureLogService.saveDisclosureLogsForHanke(hanke, "test")
            }
        }

        @Test
        fun `Returns tormaystarkastelutulos with the hanke if it has been calculated`() {
            val perus = 4.3f
            val pyoraily = 2.1f
            val linjaauto = 1.4f
            val raitiovaunu = 3f
            val hanke =
                HankeFactory.create(hankeTunnus = HANKE_TUNNUS)
                    .withTormaystarkasteluTulos(
                        perusIndeksi = perus,
                        pyorailyIndeksi = pyoraily,
                        linjaautoIndeksi = linjaauto,
                        raitiovaunuIndeksi = raitiovaunu
                    )
            every { hankeService.loadHanke(HANKE_TUNNUS) }.returns(hanke)
            every {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
            } returns true

            get(url)
                .andExpect(status().isOk)
                .andExpect(jsonPath("tormaystarkasteluTulos.perusIndeksi").value(perus))
                .andExpect(jsonPath("tormaystarkasteluTulos.pyorailyIndeksi").value(pyoraily))
                .andExpect(jsonPath("tormaystarkasteluTulos.linjaautoIndeksi").value(linjaauto))
                .andExpect(jsonPath("tormaystarkasteluTulos.raitiovaunuIndeksi").value(raitiovaunu))
                .andExpect(
                    // In this case, raitiovaunu > linjaauto
                    jsonPath("tormaystarkasteluTulos.joukkoliikenneIndeksi").value(raitiovaunu)
                )
                // In this case, perusIndeksi has the highest value
                .andExpect(
                    jsonPath("tormaystarkasteluTulos.liikennehaittaIndeksi.indeksi").value(perus)
                )
                .andExpect(
                    jsonPath("tormaystarkasteluTulos.liikennehaittaIndeksi.tyyppi")
                        .value(IndeksiType.PERUSINDEKSI.name)
                )

            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
                hankeService.loadHanke(HANKE_TUNNUS)
                disclosureLogService.saveDisclosureLogsForHanke(hanke, "test")
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
                disclosureLogService.saveDisclosureLogsForHankkeet(hankkeet, "test")
            }
        }

        @Test
        fun `When calling get with geometry=true then return all Hanke data with geometry`() {
            // faking the service call with two returned Hanke
            val hankeIds = listOf(123, 444)
            val alue1 =
                Hankealue(hankeId = hankeIds[0], geometriat = Geometriat(1, FeatureCollection()))
            val alue2 =
                Hankealue(hankeId = hankeIds[1], geometriat = Geometriat(2, FeatureCollection()))
            val hanke1 = HankeFactory.create(id = hankeIds[0], hankeTunnus = HANKE_TUNNUS)
            val hanke2 = HankeFactory.create(id = hankeIds[1], hankeTunnus = "hanketunnus2")
            hanke1.alueet = mutableListOf(alue1)
            hanke2.alueet = mutableListOf(alue2)
            val hankkeet = listOf(hanke1, hanke2)

            every { hankeService.loadHankkeetByIds(hankeIds) }.returns(hankkeet)
            every { permissionService.getAllowedHankeIds(USERNAME, PermissionCode.VIEW) }
                .returns(hankeIds)

            // we check that we get the two hankeTunnus and geometriat we expect
            get("$url/?geometry=true")
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
    }

    @Nested
    inner class GetHankeHakemukset {
        private val url = "$BASE_URL/$HANKE_TUNNUS/hakemukset"

        @Test
        @WithAnonymousUser
        fun `Without authenticated user return unauthorized (401) `() {
            get(url)
                .andExpect(SecurityMockMvcResultMatchers.unauthenticated())
                .andExpect(status().isUnauthorized)
                .andExpect(hankeError(HankeError.HAI0001))
        }

        @Test
        fun `With unknown hanke tunnus return 404`() {
            every {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
            } returns true
            every { hankeService.getHankeApplications(HANKE_TUNNUS) } throws
                HankeNotFoundException(HANKE_TUNNUS)

            get(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI1001))

            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
                hankeService.getHankeApplications(HANKE_TUNNUS)
            }
        }

        @Test
        fun `When user does not have permission return 404`() {
            every { authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name) } throws
                HankeNotFoundException(HANKE_TUNNUS)

            get(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI1001))

            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
            }
        }

        @Test
        fun `With no applications return empty list`() {
            every { hankeService.getHankeApplications(HANKE_TUNNUS) } returns listOf()
            every {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
            } returns true

            val response: ApplicationsResponse = get(url).andExpect(status().isOk).andReturnBody()

            assertTrue(response.applications.isEmpty())
            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
                hankeService.getHankeApplications(HANKE_TUNNUS)
            }
        }

        @Test
        fun `With known hanketunnus return applications`() {
            val applications = AlluDataFactory.createApplications(5)
            every { hankeService.getHankeApplications(HANKE_TUNNUS) } returns applications
            every {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
            } returns true

            val response: ApplicationsResponse = get(url).andExpect(status().isOk).andReturnBody()

            assertTrue(response.applications.isNotEmpty())
            assertEquals(ApplicationsResponse(applications), response)
            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
                hankeService.getHankeApplications(HANKE_TUNNUS)
                disclosureLogService.saveDisclosureLogsForApplications(applications, USERNAME)
            }
        }
    }

    @Nested
    inner class CreateHanke {
        private val url = BASE_URL

        @Test
        @WithAnonymousUser
        fun `Without authenticated user return unauthorized (401)`() {
            post(url, HankeFactory.create(id = null, hankeTunnus = null))
                .andExpect(SecurityMockMvcResultMatchers.unauthenticated())
                .andExpect(status().isUnauthorized)
                .andExpect(hankeError(HankeError.HAI0001))
        }

        @Test
        fun `Add Hanke and return it with newly created hankeTunnus`() {
            val hankeToBeCreated =
                HankeFactory.create(
                    id = null,
                    hankeTunnus = null,
                    version = null,
                    createdAt = null,
                    createdBy = null,
                )
            val hankeId = 1
            val createdHanke =
                hankeToBeCreated.copy(
                    id = hankeId,
                    hankeTunnus = HANKE_TUNNUS,
                    version = 0,
                    createdBy = USERNAME,
                    createdAt = getCurrentTimeUTC(),
                )
            every { hankeService.createHanke(any()) }.returns(createdHanke)

            post(url, hankeToBeCreated)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.hankeTunnus").value(HANKE_TUNNUS))
                .andExpect(content().json(createdHanke.toJsonString()))

            verifySequence {
                hankeService.createHanke(any())
                disclosureLogService.saveDisclosureLogsForHanke(createdHanke, USERNAME)
            }
        }

        @Test
        fun `Sanitize hanke input and return 200`() {
            val hanke = HankeFactory.create().withYhteystiedot().apply { generated = true }
            val expectedServiceArgument =
                HankeFactory.create(id = null).withYhteystiedot().apply { generated = false }
            every { hankeService.createHanke(expectedServiceArgument) } returns
                expectedServiceArgument

            post(url, hanke).andExpect(status().isOk)

            verifySequence {
                hankeService.createHanke(expectedServiceArgument)
                disclosureLogService.saveDisclosureLogsForHanke(any(), any())
            }
        }

        @Test
        fun `Add Hanke and HankeYhteystiedot and return it with newly created hankeTunnus (POST)`() {
            val hankeToBeMocked =
                HankeFactory.create(hankeTunnus = null, version = null, createdBy = USERNAME)
                    .withGeneratedOmistaja(1)
            val expectedHanke =
                hankeToBeMocked.apply {
                    hankeTunnus = HANKE_TUNNUS
                    id = 12
                    omistajat[0].id = 3
                }
            every { hankeService.createHanke(any()) }.returns(expectedHanke)
            val expectedContent = expectedHanke.toJsonString()

            post(url, hankeToBeMocked)
                .andExpect(status().isOk)
                .andExpect(content().json(expectedContent))
                .andExpect(jsonPath("$.hankeTunnus").value(HANKE_TUNNUS))

            verifySequence {
                hankeService.createHanke(any())
                disclosureLogService.saveDisclosureLogsForHanke(expectedHanke, USERNAME)
            }
        }

        @Test
        fun `exception in Hanke creation causes a 500 Internal Server Error response with specific HankeError`() {
            val hanke = HankeFactory.create(id = null, hankeTunnus = null)
            every { hankeService.createHanke(any()) } throws RuntimeException("Some error")

            post(url, hanke)
                .andExpect(status().isInternalServerError)
                .andExpect(hankeError(HankeError.HAI0002))

            verifySequence { hankeService.createHanke(any()) }
        }

        @Test
        fun `test dates and times do not change on a roundtrip, except for rounding to midnight`() {
            val datetimeAlku = DateFactory.getStartDatetime()
            val datetimeLoppu = DateFactory.getEndDatetime()
            val hankeToBeMocked =
                HankeFactory.create(
                    null,
                    hankeTunnus = null,
                    createdBy = USERNAME,
                    createdAt = getCurrentTimeUTC()
                )
            val alue = Hankealue()
            alue.haittaAlkuPvm = datetimeAlku
            alue.haittaLoppuPvm = datetimeLoppu
            hankeToBeMocked.alueet.add(alue)
            // Prepare the expected result/return
            // Note, "pvm" values should have become truncated to begin of the day
            val expectedDateAlku = datetimeAlku.truncatedTo(ChronoUnit.DAYS)
            val expectedDateLoppu = datetimeLoppu.truncatedTo(ChronoUnit.DAYS)
            val expectedHanke =
                hankeToBeMocked.apply {
                    hankeTunnus = HANKE_TUNNUS
                    id = 12
                }
            expectedHanke.alueet[0].haittaAlkuPvm = expectedDateAlku
            expectedHanke.alueet[0].haittaLoppuPvm = expectedDateLoppu
            val expectedContent = expectedHanke.toJsonString()
            // JSON string versions without quotes:
            val expectedDateAlkuJson =
                expectedDateAlku.toJsonString().substringAfter('"').substringBeforeLast('"')
            val expectedDateLoppuJson =
                expectedDateLoppu.toJsonString().substringAfter('"').substringBeforeLast('"')
            // faking the service call
            every { hankeService.createHanke(any()) }.returns(expectedHanke)

            // Call it and check results
            post(url, hankeToBeMocked)
                .andExpect(status().isOk)
                .andExpect(content().json(expectedContent))
                // These might be redundant, but at least it is clear what we're checking here:
                .andExpect(jsonPath("$.alkuPvm").value(expectedDateAlkuJson))
                .andExpect(jsonPath("$.loppuPvm").value(expectedDateLoppuJson))
                .andExpect(jsonPath("$.alueet[0].haittaAlkuPvm").value(expectedDateAlkuJson))
                .andExpect(jsonPath("$.alueet[0].haittaLoppuPvm").value(expectedDateLoppuJson))

            verifySequence {
                hankeService.createHanke(any())
                disclosureLogService.saveDisclosureLogsForHanke(expectedHanke, USERNAME)
            }
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
            every { hankeService.updateHanke(any()) }.returns(updatedHanke)

            put(url, hankeToBeUpdated)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.nimi").value(HankeFactory.defaultNimi))
                .andExpect(jsonPath("$.status").value(HankeStatus.PUBLIC.name))

            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.EDIT.name)
                hankeService.updateHanke(any())
                disclosureLogService.saveDisclosureLogsForHanke(updatedHanke, USERNAME)
            }
        }

        @Test
        fun `test tyomaa and haitat-fields roundtrip`() {
            val hankeToBeUpdated = HankeFactory.create(hankeTunnus = HANKE_TUNNUS)
            hankeToBeUpdated.tyomaaKatuosoite = "Testikatu 1"
            hankeToBeUpdated.tyomaaTyyppi.add(TyomaaTyyppi.VESI)
            hankeToBeUpdated.tyomaaTyyppi.add(TyomaaTyyppi.KAASUJOHTO)
            val alue = Hankealue()
            alue.haittaAlkuPvm = DateFactory.getStartDatetime()
            alue.haittaLoppuPvm = DateFactory.getEndDatetime()
            alue.kaistaHaitta = TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.KAKSI
            alue.kaistaPituusHaitta = KaistajarjestelynPituus.NELJA
            alue.meluHaitta = Haitta13.YKSI
            alue.polyHaitta = Haitta13.KAKSI
            alue.tarinaHaitta = Haitta13.KOLME
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
            every { hankeService.updateHanke(any()) } returns expectedHanke

            put(url, hankeToBeUpdated)
                .andExpect(status().isOk)
                .andExpect(content().json(expectedContent))
                // These might be redundant, but at least it is clear what we're checking here:
                .andExpect(jsonPath("$.tyomaaKatuosoite").value("Testikatu 1"))
                .andExpect(
                    jsonPath("$.alueet[0].kaistaHaitta")
                        .value(TodennakoinenHaittaPaaAjoRatojenKaistajarjestelyihin.KAKSI.name)
                ) // Note, here as string, not the enum.

            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.EDIT.name)
                hankeService.updateHanke(any())
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
