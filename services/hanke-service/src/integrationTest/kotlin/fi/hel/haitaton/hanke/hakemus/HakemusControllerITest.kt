package fi.hel.haitaton.hanke.hakemus

import assertk.all
import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.extracting
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.key
import assertk.assertions.prop
import assertk.assertions.single
import com.fasterxml.jackson.databind.node.ObjectNode
import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeErrorDetail
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.OBJECT_MAPPER
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.andReturnBody
import fi.hel.haitaton.hanke.andReturnContent
import fi.hel.haitaton.hanke.domain.CreateHankeRequest
import fi.hel.haitaton.hanke.factory.CreateHakemusRequestFactory
import fi.hel.haitaton.hanke.factory.HaittaFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory.Companion.withExtras
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withRegistryKey
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withTimes
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withWorkDescription
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.MuutosilmoitusFactory
import fi.hel.haitaton.hanke.factory.PaatosFactory
import fi.hel.haitaton.hanke.factory.PaperDecisionReceiverFactory
import fi.hel.haitaton.hanke.factory.TaydennysAttachmentFactory
import fi.hel.haitaton.hanke.factory.TaydennysFactory
import fi.hel.haitaton.hanke.factory.TaydennysFactory.Companion.withExtras
import fi.hel.haitaton.hanke.factory.TaydennyspyyntoFactory
import fi.hel.haitaton.hanke.geometria.GeometriatDao
import fi.hel.haitaton.hanke.getResourceAsBytes
import fi.hel.haitaton.hanke.hankeError
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.paatos.PaatosTila.KORVATTU
import fi.hel.haitaton.hanke.paatos.PaatosTila.NYKYINEN
import fi.hel.haitaton.hanke.paatos.PaatosTyyppi.PAATOS
import fi.hel.haitaton.hanke.paatos.PaatosTyyppi.TOIMINNALLINEN_KUNTO
import fi.hel.haitaton.hanke.paatos.PaatosTyyppi.TYO_VALMIS
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.taydennys.TaydennysWithExtras
import fi.hel.haitaton.hanke.test.USERNAME
import fi.hel.haitaton.hanke.toJsonString
import fi.hel.haitaton.hanke.valmistumisilmoitus.ValmistumisilmoitusFactory
import fi.hel.haitaton.hanke.valmistumisilmoitus.ValmistumisilmoitusResponse
import fi.hel.haitaton.hanke.valmistumisilmoitus.ValmistumisilmoitusType
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import io.mockk.verifySequence
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithAnonymousUser
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.ResultMatcher
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private const val HANKE_TUNNUS = "HAI-1234"

@WebMvcTest(controllers = [HakemusController::class])
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class HakemusControllerITest(@Autowired override val mockMvc: MockMvc) : ControllerTest {

    @Autowired private lateinit var hakemusService: HakemusService
    @Autowired private lateinit var hankeService: HankeService
    @Autowired private lateinit var authorizer: HakemusAuthorizer
    @Autowired private lateinit var disclosureLogService: DisclosureLogService

    private val id = 1234L

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(hakemusService, hankeService, authorizer)
    }

    @Nested
    inner class GetById {
        private val url = "/hakemukset/$id"

        @Test
        @WithAnonymousUser
        fun `when unknown user should return 401`() {
            get(url).andExpect(status().isUnauthorized)

            verifySequence { hakemusService wasNot Called }
        }

        @Test
        fun `when application does not exist should return 404`() {
            every { authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name) } throws
                HakemusNotFoundException(id)

            get(url).andExpect(status().isNotFound)

            verifySequence { authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name) }
        }

        @ParameterizedTest
        @EnumSource(ApplicationType::class)
        fun `returns hakemus and writes it to disclosure logs when it exists`(
            applicationType: ApplicationType
        ) {
            val hakemus =
                HakemusFactory.create(
                    id = id,
                    applicationType = applicationType,
                    hankeTunnus = HANKE_TUNNUS,
                )
            every { authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name) } returns true
            every { hakemusService.getWithExtras(id) } returns hakemus.withExtras()

            get(url)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.hankeTunnus").value(HANKE_TUNNUS))
                .andExpect(jsonPath("$.applicationType").value(applicationType.name))
                .andExpect(
                    jsonPath("$.applicationData.applicationType").value(applicationType.name)
                )
                .andExpect(jsonPath("$.paatokset").isMap())
                .andExpect(jsonPath("$.paatokset").isEmpty())
                .andExpect(jsonPath("$.taydennyspyynto").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.muutosilmoitus").doesNotHaveJsonPath())

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name)
                hakemusService.getWithExtras(id)
                disclosureLogService.saveForHakemusResponse(hakemus.toResponse(), USERNAME)
            }
        }

        @ParameterizedTest
        @EnumSource(ApplicationType::class)
        fun `returns paatokset when they exist`(applicationType: ApplicationType) {
            every { authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name) } returns true
            val hakemus =
                HakemusFactory.create(
                    id = id,
                    applicationType = applicationType,
                    hankeTunnus = HANKE_TUNNUS,
                )
            val paatokset =
                listOf(
                    PaatosFactory.createForHakemus(hakemus, "KP2400001", PAATOS, KORVATTU),
                    PaatosFactory.createForHakemus(hakemus, "KP2400001-1", PAATOS, KORVATTU),
                    PaatosFactory.createForHakemus(hakemus, "KP2400001-2", PAATOS, NYKYINEN),
                    PaatosFactory.createForHakemus(
                        hakemus,
                        "KP2400001-1",
                        TOIMINNALLINEN_KUNTO,
                        KORVATTU,
                    ),
                    PaatosFactory.createForHakemus(
                        hakemus,
                        "KP2400001-2",
                        TOIMINNALLINEN_KUNTO,
                        NYKYINEN,
                    ),
                    PaatosFactory.createForHakemus(hakemus, "KP2400001-2", TYO_VALMIS, NYKYINEN),
                )
            every { hakemusService.getWithExtras(id) } returns hakemus.withExtras(paatokset)

            get(url)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.paatokset").isMap())
                .andExpect(jsonPath("$.paatokset.KP2400001[0].tyyppi").value(PAATOS.name))
                .andExpect(jsonPath("$.paatokset.KP2400001[0].tila").value(KORVATTU.name))
                .andExpect(jsonPath("$.paatokset.KP2400001[1]").doesNotExist())
                .andExpect(jsonPath("$.paatokset.KP2400001-1[0].tyyppi").value(PAATOS.name))
                .andExpect(jsonPath("$.paatokset.KP2400001-1[0].tila").value(KORVATTU.name))
                .andExpect(
                    jsonPath("$.paatokset.KP2400001-1[1].tyyppi").value(TOIMINNALLINEN_KUNTO.name)
                )
                .andExpect(jsonPath("$.paatokset.KP2400001-1[1].tila").value(KORVATTU.name))
                .andExpect(jsonPath("$.paatokset.KP2400001-1[2]").doesNotExist())
                .andExpect(jsonPath("$.paatokset.KP2400001-2[0].tyyppi").value(PAATOS.name))
                .andExpect(jsonPath("$.paatokset.KP2400001-2[0].tila").value(NYKYINEN.name))
                .andExpect(
                    jsonPath("$.paatokset.KP2400001-2[1].tyyppi").value(TOIMINNALLINEN_KUNTO.name)
                )
                .andExpect(jsonPath("$.paatokset.KP2400001-2[1].tila").value(NYKYINEN.name))
                .andExpect(jsonPath("$.paatokset.KP2400001-2[2].tyyppi").value(TYO_VALMIS.name))
                .andExpect(jsonPath("$.paatokset.KP2400001-2[2].tila").value(NYKYINEN.name))
                .andExpect(jsonPath("$.paatokset.KP2400001-2[3]").doesNotExist())

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name)
                hakemusService.getWithExtras(id)
            }
        }

        @ParameterizedTest
        @EnumSource(ApplicationType::class)
        fun `returns taydennyspyynto when it exists`(applicationType: ApplicationType) {
            val hakemus =
                HakemusFactory.create(
                    id = id,
                    applicationType = applicationType,
                    hankeTunnus = HANKE_TUNNUS,
                )
            val taydennyspyynto = TaydennyspyyntoFactory.create()
            every { hakemusService.getWithExtras(id) } returns
                hakemus.withExtras(taydennyspyynto = taydennyspyynto)
            every { authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name) } returns true

            get(url)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.taydennyspyynto").exists())
                .andExpect(
                    jsonPath("$.taydennyspyynto.id")
                        .value(TaydennyspyyntoFactory.DEFAULT_ID.toString())
                )
                .andExpect(jsonPath("$.taydennyspyynto.kentat[0].key").value("CUSTOMER"))
                .andExpect(
                    jsonPath("$.taydennyspyynto.kentat[0].message").value("Customer is missing")
                )
                .andExpect(jsonPath("$.taydennyspyynto.kentat[1].key").value("ATTACHMENT"))
                .andExpect(
                    jsonPath("$.taydennyspyynto.kentat[1].message")
                        .value("Needs a letter of attorney")
                )

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name)
                hakemusService.getWithExtras(id)
            }
        }

        @ParameterizedTest
        @EnumSource(ApplicationType::class)
        fun `returns taydennys and writes it to disclosure logs when it exists`(
            applicationType: ApplicationType
        ) {
            val hakemus =
                HakemusFactory.create(
                    id = id,
                    applicationType = applicationType,
                    hankeTunnus = HANKE_TUNNUS,
                )
            val taydennys: TaydennysWithExtras =
                TaydennysFactory.create(
                        hakemusId = hakemus.id,
                        hakemusData = hakemus.applicationData,
                    )
                    .withExtras()
            every { hakemusService.getWithExtras(id) } returns
                hakemus.withExtras(taydennys = taydennys)
            every { authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name) } returns true

            get(url)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.taydennys").exists())
                .andExpect(jsonPath("$.taydennys.id").value(TaydennysFactory.DEFAULT_ID.toString()))
                .andExpect(
                    jsonPath("$.taydennys.applicationData.applicationType")
                        .value(applicationType.name)
                )
                .andExpect(jsonPath("$.taydennys.muutokset").isArray)
                .andExpect(jsonPath("$.taydennys.muutokset").isEmpty)
                .andExpect(jsonPath("$.taydennys.liitteet").isArray)
                .andExpect(jsonPath("$.taydennys.liitteet").isEmpty)

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name)
                hakemusService.getWithExtras(id)
                disclosureLogService.saveForHakemusResponse(any(), USERNAME)
                disclosureLogService.saveForTaydennys(taydennys.toResponse().taydennys, USERNAME)
            }
        }

        @ParameterizedTest
        @EnumSource(ApplicationType::class)
        fun `returns muutokset when taydennys is different from the hakemus`(
            applicationType: ApplicationType
        ) {
            val hakemus =
                HakemusFactory.create(
                    id = id,
                    applicationType = applicationType,
                    hankeTunnus = HANKE_TUNNUS,
                )
            val muutokset = listOf("name", "areas[1]")
            val taydennys: TaydennysWithExtras =
                TaydennysFactory.create(
                        hakemusId = hakemus.id,
                        hakemusData = hakemus.applicationData,
                    )
                    .withExtras(muutokset = muutokset)
            every { hakemusService.getWithExtras(id) } returns
                hakemus.withExtras(taydennys = taydennys)
            every { authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name) } returns true

            get(url)
                .andExpect(status().isOk)
                .andExpect(jsonPath("taydennys").exists())
                .andExpect(jsonPath("taydennys.muutokset[0]").value(muutokset[0]))
                .andExpect(jsonPath("taydennys.muutokset[1]").value(muutokset[1]))

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name)
                hakemusService.getWithExtras(id)
                disclosureLogService.saveForHakemusResponse(any(), USERNAME)
                disclosureLogService.saveForTaydennys(taydennys.toResponse().taydennys, USERNAME)
            }
        }

        @ParameterizedTest
        @EnumSource(ApplicationType::class)
        fun `returns taydennys liitteet with hakemus`(applicationType: ApplicationType) {
            val hakemus =
                HakemusFactory.create(
                    id = id,
                    applicationType = applicationType,
                    hankeTunnus = HANKE_TUNNUS,
                )
            val liitteet =
                listOf(
                    TaydennysAttachmentFactory.create(fileName = "First"),
                    TaydennysAttachmentFactory.create(fileName = "Second"),
                )
            val taydennys: TaydennysWithExtras =
                TaydennysFactory.create(
                        hakemusId = hakemus.id,
                        hakemusData = hakemus.applicationData,
                    )
                    .withExtras(liitteet = liitteet)
            every { hakemusService.getWithExtras(id) } returns
                hakemus.withExtras(taydennys = taydennys)
            every { authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name) } returns true

            get(url)
                .andExpect(status().isOk)
                .andExpect(jsonPath("taydennys").exists())
                .andExpect(jsonPath("taydennys.liitteet[0].fileName").value("First"))
                .andExpect(jsonPath("taydennys.liitteet[1].fileName").value("Second"))

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name)
                hakemusService.getWithExtras(id)
                disclosureLogService.saveForHakemusResponse(any(), USERNAME)
                disclosureLogService.saveForTaydennys(taydennys.toResponse().taydennys, USERNAME)
            }
        }

        @ParameterizedTest
        @EnumSource(ApplicationType::class)
        fun `returns muutosilmoitus and writes it to disclosure logs when it exists`(
            applicationType: ApplicationType
        ) {
            val hakemus =
                HakemusFactory.create(
                    id = id,
                    applicationType = applicationType,
                    hankeTunnus = HANKE_TUNNUS,
                )
            val muutosilmoitus =
                MuutosilmoitusFactory.create(
                    hakemusId = hakemus.id,
                    hakemusData = hakemus.applicationData,
                )
            every { hakemusService.getWithExtras(id) } returns
                hakemus.withExtras(muutosilmoitus = muutosilmoitus)
            every { authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name) } returns true

            get(url)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.muutosilmoitus").exists())
                .andExpect(
                    jsonPath("$.muutosilmoitus.id")
                        .value(MuutosilmoitusFactory.DEFAULT_ID.toString())
                )
                .andExpect(
                    jsonPath("$.muutosilmoitus.applicationData.applicationType")
                        .value(applicationType.name)
                )

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name)
                hakemusService.getWithExtras(id)
                disclosureLogService.saveForHakemusResponse(any(), USERNAME)
                disclosureLogService.saveForMuutosilmoitus(muutosilmoitus.toResponse(), USERNAME)
            }
        }

        @Test
        fun `returns valmistumisilmoitukset with a kaivuilmoitus`() {
            val hakemus =
                HakemusFactory.create(
                    applicationType = ApplicationType.EXCAVATION_NOTIFICATION,
                    valmistumisilmoitukset =
                        listOf(
                            ValmistumisilmoitusFactory.create(
                                type = ValmistumisilmoitusType.TYO_VALMIS
                            ),
                            ValmistumisilmoitusFactory.create(
                                dateReported = LocalDate.parse("2024-12-24")
                            ),
                        ),
                )
            every { authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name) } returns true
            every { hakemusService.getWithExtras(id) } returns hakemus.withExtras()

            // Jackson can't deserialize HakemusWithExtrasResponse because of the @JsonUnwrapped, so
            // deserialize to HakemusResponse and check the basic fields from it.
            val response: HakemusResponse = get(url).andExpect(status().isOk).andReturnBody()

            assertThat(response.valmistumisilmoitukset).isNotNull().all {
                key(ValmistumisilmoitusType.TOIMINNALLINEN_KUNTO).single().all {
                    prop(ValmistumisilmoitusResponse::dateReported)
                        .isEqualTo(LocalDate.parse("2024-12-24"))
                    prop(ValmistumisilmoitusResponse::reportedAt)
                        .isEqualTo(ValmistumisilmoitusFactory.DEFAULT_REPORT_TIME)
                }
                key(ValmistumisilmoitusType.TYO_VALMIS).single().all {
                    prop(ValmistumisilmoitusResponse::dateReported)
                        .isEqualTo(ValmistumisilmoitusFactory.DEFAULT_DATE)
                    prop(ValmistumisilmoitusResponse::reportedAt)
                        .isEqualTo(ValmistumisilmoitusFactory.DEFAULT_REPORT_TIME)
                }
            }
            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name)
                hakemusService.getWithExtras(id)
            }
        }

        @Test
        fun `returns empty map in valmistumisilmoitukset when kaivuilmoitus has none`() {
            val hakemus =
                HakemusFactory.create(applicationType = ApplicationType.EXCAVATION_NOTIFICATION)
            every { authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name) } returns true
            every { hakemusService.getWithExtras(id) } returns hakemus.withExtras()

            get(url)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.valmistumisilmoitukset").isMap())
                .andExpect(jsonPath("$.valmistumisilmoitukset").isEmpty())

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name)
                hakemusService.getWithExtras(id)
            }
        }

        @Test
        fun `doesn't include valmistumisilmoitukset with a johtoselvityshakemus`() {
            val hakemus =
                HakemusFactory.create(
                    applicationType = ApplicationType.CABLE_REPORT,
                    valmistumisilmoitukset =
                        listOf(
                            ValmistumisilmoitusFactory.create(),
                            ValmistumisilmoitusFactory.create(
                                type = ValmistumisilmoitusType.TYO_VALMIS
                            ),
                            ValmistumisilmoitusFactory.create(
                                dateReported = LocalDate.parse("2024-12-24")
                            ),
                        ),
                )
            every { authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name) } returns true
            every { hakemusService.getWithExtras(id) } returns hakemus.withExtras()

            get(url)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.valmistumisilmoitukset").doesNotHaveJsonPath())

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name)
                hakemusService.getWithExtras(id)
            }
        }

        @Test
        fun `returns haittojenhallintasuunnitelma with a kaivuilmoitus`() {
            val hakemus =
                HakemusFactory.create(applicationType = ApplicationType.EXCAVATION_NOTIFICATION)
            every { authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name) } returns true
            every { hakemusService.getWithExtras(id) } returns hakemus.withExtras()
            val haittojenhallintaPath = "$.applicationData.areas[0].haittojenhallintasuunnitelma"

            get(url)
                .andExpect(status().isOk)
                .andExpect(jsonPath(haittojenhallintaPath).isMap())
                .andExpect(
                    jsonPath("$haittojenhallintaPath.PYORALIIKENNE")
                        .value(HaittaFactory.DEFAULT_HHS_PYORALIIKENNE)
                )

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name)
                hakemusService.getWithExtras(id)
            }
        }

        @ParameterizedTest
        @EnumSource(CustomerType::class, names = ["PERSON", "OTHER"])
        fun `hides the registry key of person customers when it's not null`(tyyppi: CustomerType) {
            val hakemusdata = HakemusFactory.hakemusDataForRegistryKeyTest(tyyppi)
            val hakemus =
                HakemusFactory.create(
                    id = id,
                    hankeTunnus = HANKE_TUNNUS,
                    applicationData = hakemusdata,
                )
            every { authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name) } returns true
            every { hakemusService.getWithExtras(id) } returns hakemus.withExtras()

            get(url).andExpect(status().isOk).andVerifyRegistryKeys()

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name)
                hakemusService.getWithExtras(id)
            }
        }
    }

    @Nested
    inner class GetHankkeenHakemukset {
        private val url = "/hankkeet/$HANKE_TUNNUS/hakemukset"

        @Test
        @WithAnonymousUser
        fun `returns 401 when user is unauthenticated`() {
            get(url)
                .andExpect(SecurityMockMvcResultMatchers.unauthenticated())
                .andExpect(status().isUnauthorized)
                .andExpect(hankeError(HankeError.HAI0001))
        }

        @Test
        fun `return 404 when hanketunnus is unknown`() {
            every {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
            } returns true
            every { hakemusService.hankkeenHakemuksetResponses(HANKE_TUNNUS, false) } throws
                HankeNotFoundException(HANKE_TUNNUS)

            get(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI1001))

            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
                hakemusService.hankkeenHakemuksetResponses(HANKE_TUNNUS, false)
            }
        }

        @Test
        fun `returns 404 when user does not have permission for the hanke`() {
            every { authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name) } throws
                HankeNotFoundException(HANKE_TUNNUS)

            get(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI1001))

            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
            }
        }

        @Test
        fun `returns empty list when there are no applications`() {
            every { hakemusService.hankkeenHakemuksetResponses(HANKE_TUNNUS, false) } returns
                emptyList()
            every {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
            } returns true

            val response: HankkeenHakemuksetResponse =
                get(url).andExpect(status().isOk).andReturnBody()

            assertThat(response.applications).isEmpty()
            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
                hakemusService.hankkeenHakemuksetResponses(HANKE_TUNNUS, false)
            }
        }

        @Test
        fun `return applications without areas when areas parameter not specified`() {
            val hakemukset = HakemusFactory.createSeveralHankkeenHakemusResponses(false)
            every { hakemusService.hankkeenHakemuksetResponses(HANKE_TUNNUS, false) } returns
                hakemukset
            every {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
            } returns true

            val response: HankkeenHakemuksetResponse =
                get(url)
                    .andExpect(status().isOk)
                    .andExpect(jsonPath("$.applications[*].applicationData.areas").hasJsonPath())
                    .andExpect(jsonPath("$.applications[0].muutosilmoitus").exists())
                    .andExpect(jsonPath("$.applications[1].muutosilmoitus").doesNotHaveJsonPath())
                    .andReturnBody()

            assertThat(response.applications).isNotEmpty()
            assertThat(response.applications)
                .extracting { it.applicationData.areas }
                .each { it.isNull() }
            val expected = HankkeenHakemuksetResponse(hakemukset)
            assertThat(response).isEqualTo(expected)
            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
                hakemusService.hankkeenHakemuksetResponses(HANKE_TUNNUS, false)
            }
        }

        @Test
        fun `returns application areas when areas parameter is true`() {
            val responses = HakemusFactory.createSeveralHankkeenHakemusResponses(true)
            every { hakemusService.hankkeenHakemuksetResponses(HANKE_TUNNUS, true) } returns
                responses
            every {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
            } returns true

            val response: HankkeenHakemuksetResponse =
                get("$url?areas=true").andExpect(status().isOk).andReturnBody()

            for (i in 0..3) {
                assertThat(response.applications[i].applicationData.areas)
                    .isNotNull()
                    .single()
                    .isEqualTo(responses[i].applicationData.areas?.single())
            }
            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
                hakemusService.hankkeenHakemuksetResponses(HANKE_TUNNUS, true)
            }
        }
    }

    @Nested
    inner class Create {
        private val url = "/hakemukset"
        private val hankeTunnus = "HAI24-94"

        val request = CreateHakemusRequestFactory.johtoselvitysRequest(hankeTunnus = hankeTunnus)

        @Test
        @WithAnonymousUser
        fun `returns 401 when unknown user`() {
            post(url, request)
                .andExpect(status().isUnauthorized)
                .andExpect(hankeError(HankeError.HAI0001))
        }

        @Test
        fun `returns 400 when no request body`() {
            post(url).andExpect(status().isBadRequest).andExpect(hankeError(HankeError.HAI0003))
        }

        @Test
        fun `returns 404 when lacking permissions`() {
            every { authorizer.authorizeCreate(request) } throws HankeNotFoundException(hankeTunnus)

            post(url, request)
                .andExpect(status().isNotFound)
                .andExpect(hankeError(HankeError.HAI1001))

            verifySequence { authorizer.authorizeCreate(request) }
        }

        @Test
        fun `returns 400 when request doesn't pass validation`() {
            val request = request.copy(name = " ")
            every { authorizer.authorizeCreate(request) } returns true

            post(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI2008))

            verifySequence { authorizer.authorizeCreate(request) }
        }

        @Test
        fun `returns 404 when hanke not found`() {
            every { authorizer.authorizeCreate(request) } returns true
            every { hakemusService.create(request, USERNAME) } throws
                HankeNotFoundException(hankeTunnus)

            post(url, request)
                .andExpect(status().isNotFound)
                .andExpect(hankeError(HankeError.HAI1001))

            verifySequence {
                authorizer.authorizeCreate(request)
                hakemusService.create(request, USERNAME)
            }
        }

        @Test
        fun `returns 200 and the created hakemus`() {
            every { authorizer.authorizeCreate(request) } returns true
            val hakemus = HakemusFactory.create(hankeTunnus = hankeTunnus)
            every { hakemusService.create(request, USERNAME) } returns hakemus

            val response: HakemusResponse =
                post(url, request).andExpect(status().isOk).andReturnBody()

            assertThat(response).all {
                prop(HakemusResponse::id).isEqualTo(hakemus.id)
                prop(HakemusResponse::hankeTunnus).isEqualTo(hankeTunnus)
                prop(HakemusResponse::valmistumisilmoitukset).isNull()
            }
            verifySequence {
                authorizer.authorizeCreate(request)
                hakemusService.create(request, USERNAME)
            }
        }

        @Test
        fun `returns 200 and the created hakemus when the hakemus is a kaivuilmoitus`() {
            val request = CreateHakemusRequestFactory.kaivuilmoitusRequest()
            every { authorizer.authorizeCreate(request) } returns true
            val hakemus =
                HakemusFactory.create(
                    applicationType = ApplicationType.EXCAVATION_NOTIFICATION,
                    hankeTunnus = hankeTunnus,
                )
            every { hakemusService.create(request, USERNAME) } returns hakemus

            val response: HakemusResponse =
                post(url, request).andExpect(status().isOk).andReturnBody()

            assertThat(response).all {
                prop(HakemusResponse::id).isEqualTo(hakemus.id)
                prop(HakemusResponse::hankeTunnus).isEqualTo(hankeTunnus)
                prop(HakemusResponse::valmistumisilmoitukset).isNotNull().isEmpty()
            }
            verifySequence {
                authorizer.authorizeCreate(request)
                hakemusService.create(request, USERNAME)
            }
        }
    }

    @Nested
    inner class CreateWithGeneratedHanke {
        private val url = "/johtoselvityshakemus"
        private val hakemusNimi = "Cool digs."

        @Test
        @WithAnonymousUser
        fun `returns 401 when unknown user`() {
            val request = CreateHankeRequest(hakemusNimi, HankeFactory.DEFAULT_HANKE_PERUSTAJA)

            post(url, request).andExpect(status().isUnauthorized)
        }

        @Test
        fun `returns 400 when no request body`() {
            post(url).andExpect(status().isBadRequest)
        }

        @Test
        fun `returns 400 when request doesn't pass validation`() {
            val request =
                CreateHankeRequest(
                    hakemusNimi,
                    HankeFactory.DEFAULT_HANKE_PERUSTAJA.copy(sahkoposti = ""),
                )

            post(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI1002))
        }

        @Test
        fun `returns hakemus after creating hanke and hakemus`() {
            val request = CreateHankeRequest(hakemusNimi, HankeFactory.DEFAULT_HANKE_PERUSTAJA)
            val hakemus = HakemusFactory.create()
            every { hankeService.generateHankeWithJohtoselvityshakemus(request, any()) } returns
                hakemus

            val response = post(url, request).andExpect(status().isOk).andReturnContent()

            val expectedResponse = hakemus.toResponse()
            JSONAssert.assertEquals(
                expectedResponse.toJsonString(),
                response,
                JSONCompareMode.NON_EXTENSIBLE,
            )
            verifySequence { hankeService.generateHankeWithJohtoselvityshakemus(request, any()) }
        }
    }

    @Nested
    inner class Update {
        private val url = "/hakemukset/$id"

        @Test
        @WithAnonymousUser
        fun `returns 401 when unknown user`() {
            put(url, HakemusUpdateRequestFactory.createBlankJohtoselvityshakemusUpdateRequest())
                .andExpect(status().isUnauthorized)

            verifySequence { hakemusService wasNot Called }
        }

        @Test
        fun `returns 400 when no request body`() {
            put(url).andExpect(status().isBadRequest)

            verifySequence { hakemusService wasNot Called }
        }

        @Test
        fun `returns 400 when end date before start date`() {
            val request =
                HakemusUpdateRequestFactory.createBlankJohtoselvityshakemusUpdateRequest()
                    .withTimes(
                        startTime = ZonedDateTime.now(),
                        endTime = ZonedDateTime.now().minusDays(1),
                    )
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true

            val response = put(url, request).andExpect(status().isBadRequest).andReturnContent()

            assertThat(response)
                .isEqualTo(HankeErrorDetail(HankeError.HAI2008, listOf("endTime")).toJsonString())
            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService wasNot Called
            }
        }

        @Test
        fun `returns 400 when invalid y-tunnus`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
                    .withRegistryKey("281192-937W")
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true

            put(url, request).andExpect(status().isBadRequest)

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService wasNot Called
            }
        }

        @Test
        fun `returns 400 when missing required data`() {
            val mockErrorPaths = listOf("workDescription")
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
                    .withWorkDescription(" ")
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true

            val response = put(url, request).andExpect(status().isBadRequest).andReturnContent()

            assertThat(response)
                .isEqualTo(HankeErrorDetail(HankeError.HAI2008, mockErrorPaths).toJsonString())
            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService wasNot Called
            }
        }

        @Test
        fun `returns 404 when no application`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } throws HakemusNotFoundException(id)

            put(url, request)
                .andExpect(status().isNotFound)
                .andExpect(hankeError(HankeError.HAI2001))

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            }
        }

        @Test
        fun `returns 409 when application sent to Allu`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.updateHakemus(id, request, USERNAME) } throws
                HakemusAlreadySentException(id, 21, ApplicationStatus.HANDLING)

            put(url, request)
                .andExpect(status().isConflict)
                .andExpect(hankeError(HankeError.HAI2009))

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.updateHakemus(id, request, USERNAME)
            }
        }

        @Test
        fun `returns 400 when request is not of the same type as the application`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.updateHakemus(id, request, USERNAME) } throws
                IncompatibleHakemusUpdateRequestException(
                    HakemusFactory.create(id = id),
                    KaivuilmoitusEntityData::class,
                    JohtoselvityshakemusUpdateRequest::class,
                )

            put(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI2002))

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.updateHakemus(id, request, USERNAME)
            }
        }

        @Test
        fun `returns 400 when request areas contain invalid geometry`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.updateHakemus(id, request, USERNAME) } throws
                HakemusGeometryException(GeometriatDao.InvalidDetail("", ""))

            put(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI2005))

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.updateHakemus(id, request, USERNAME)
            }
        }

        @Test
        fun `returns 400 when request areas outside hankealueet`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.updateHakemus(id, request, USERNAME) } throws
                HakemusGeometryNotInsideHankeException("Message")

            put(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI2007))

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.updateHakemus(id, request, USERNAME)
            }
        }

        @Test
        fun `returns 400 when request contain invalid customer`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.updateHakemus(id, request, USERNAME) } throws
                InvalidHakemusyhteystietoException(UUID.randomUUID(), UUID.randomUUID())

            put(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI2010))

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.updateHakemus(id, request, USERNAME)
            }
        }

        @Test
        fun `returns 400 when request contain invalid use of hidden registry key`() {
            val request = HakemusUpdateRequestFactory.createFilledKaivuilmoitusUpdateRequest()
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.updateHakemus(id, request, USERNAME) } throws
                InvalidHiddenRegistryKey(
                    "Reason for error",
                    CustomerType.COMPANY,
                    CustomerType.PERSON,
                )

            put(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI2010))

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.updateHakemus(id, request, USERNAME)
            }
        }

        @Test
        fun `returns 400 when request contain invalid contact`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.updateHakemus(id, request, USERNAME) } throws
                InvalidHakemusyhteyshenkiloException(setOf())

            put(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI2011))

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.updateHakemus(id, request, USERNAME)
            }
        }

        @ParameterizedTest
        @EnumSource(ApplicationType::class)
        fun `returns application when it exists`(applicationType: ApplicationType) {
            val hakemus = HakemusFactory.create(applicationType = applicationType)
            val request = HakemusUpdateRequestFactory.createFilledUpdateRequest(applicationType)
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.updateHakemus(id, request, USERNAME) } returns hakemus

            val response = put(url, request).andExpect(status().isOk).andReturnContent()

            val expectedResponse = hakemus.toResponse()
            JSONAssert.assertEquals(
                expectedResponse.toJsonString(),
                response,
                JSONCompareMode.NON_EXTENSIBLE,
            )
            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.updateHakemus(id, request, USERNAME)
                disclosureLogService.saveForHakemusResponse(expectedResponse, USERNAME)
            }
        }

        @ParameterizedTest
        @EnumSource(CustomerType::class, names = ["PERSON", "OTHER"])
        fun `hides the registry key of person customers when it's not null`(tyyppi: CustomerType) {
            val hakemusdata = HakemusFactory.hakemusDataForRegistryKeyTest(tyyppi)
            val hakemus =
                HakemusFactory.create(
                    id = id,
                    hankeTunnus = HANKE_TUNNUS,
                    applicationData = hakemusdata,
                )
            val request =
                HakemusUpdateRequestFactory.createFilledUpdateRequest(hakemus.applicationType)
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.updateHakemus(id, request, USERNAME) } returns hakemus

            put(url, request).andExpect(status().isOk).andVerifyRegistryKeys()

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.updateHakemus(id, request, USERNAME)
                disclosureLogService.saveForHakemusResponse(hakemus.toResponse(), USERNAME)
            }
        }
    }

    abstract inner class ReportCompletion(
        val url: String,
        val ilmoitusType: ValmistumisilmoitusType,
    ) {
        private val date = LocalDate.of(2024, 8, 8)
        private val request = DateReportRequest(date)

        @Test
        @WithAnonymousUser
        fun `returns 401 when unknown user`() {
            post(url, request).andExpect(status().isUnauthorized)

            verifySequence { hakemusService wasNot Called }
        }

        @Test
        fun `returns 400 when no request body`() {
            post(url).andExpect(status().isBadRequest)

            verifySequence { hakemusService wasNot Called }
        }

        @Test
        fun `returns 200 when date is sent successfully`() {
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            justRun { hakemusService.reportCompletionDate(ilmoitusType, id, date) }

            post(url, request).andExpect(status().isOk).andExpect(content().string(""))

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.reportCompletionDate(ilmoitusType, id, date)
            }
        }

        @Test
        fun `returns 404 when the application is not found`() {
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } throws HakemusNotFoundException(id)

            post(url, request)
                .andExpect(status().isNotFound)
                .andExpect(hankeError(HankeError.HAI2001))

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            }
        }

        @Test
        fun `returns 409 when the application is not yet in Allu`() {
            val exception = HakemusNotYetInAlluException(HakemusFactory.create(id = id))

            withMockedException(exception) {
                post(url, request)
                    .andExpect(status().isConflict)
                    .andExpect(hankeError(HankeError.HAI2013))
            }
        }

        @Test
        fun `returns 400 when the application is not a kaivuilmoitus`() {
            val exception =
                WrongHakemusTypeException(
                    HakemusFactory.create(id = id),
                    ApplicationType.CABLE_REPORT,
                    listOf(ApplicationType.EXCAVATION_NOTIFICATION),
                )

            withMockedException(exception) {
                post(url, request)
                    .andExpect(status().isBadRequest)
                    .andExpect(hankeError(HankeError.HAI2002))
            }
        }

        @Test
        fun `returns 400 when the date is invalid`() {
            val exception =
                CompletionDateException(
                    ilmoitusType,
                    "Date is in the future.",
                    date,
                    HakemusFactory.create(id = id),
                )

            withMockedException(exception) {
                post(url, request)
                    .andExpect(status().isBadRequest)
                    .andExpect(hankeError(HankeError.HAI2014))
            }
        }

        @Test
        fun `returns 409 when the application is not in an allowed status`() {
            val exception =
                HakemusInWrongStatusException(
                    HakemusFactory.create(),
                    ApplicationStatus.PENDING_CLIENT,
                    allowed = listOf(),
                )

            withMockedException(exception) {
                post(url, request)
                    .andExpect(status().isConflict)
                    .andExpect(hankeError(HankeError.HAI2015))
            }
        }

        private fun withMockedException(ex: RuntimeException, f: () -> Unit) {
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.reportCompletionDate(ilmoitusType, id, date) } throws ex

            f()

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.reportCompletionDate(ilmoitusType, id, date)
            }
        }
    }

    @Nested
    inner class ReportOperationalCondition :
        ReportCompletion(
            url = "/hakemukset/$id/toiminnallinen-kunto",
            ilmoitusType = ValmistumisilmoitusType.TOIMINNALLINEN_KUNTO,
        )

    @Nested
    inner class ReportWorkFinished :
        ReportCompletion(
            url = "/hakemukset/$id/tyo-valmis",
            ilmoitusType = ValmistumisilmoitusType.TYO_VALMIS,
        )

    @Nested
    inner class SendHakemus {
        private val url = "/hakemukset/$id/laheta"

        @Test
        @WithAnonymousUser
        fun `returns 401 when unknown user`() {
            put(url, HakemusUpdateRequestFactory.createBlankJohtoselvityshakemusUpdateRequest())
                .andExpect(status().isUnauthorized)
                .andExpect(hankeError(HankeError.HAI0001))
        }

        @Test
        fun `returns 404 when missing permission`() {
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } throws HakemusNotFoundException(id)

            post(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI2001))

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            }
        }

        @Test
        fun `returns 404 when application not found`() {
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.sendHakemus(id, null, USERNAME) } throws
                HakemusNotFoundException(id)

            post(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI2001))

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.sendHakemus(id, null, USERNAME)
            }
        }

        @Test
        fun `returns 403 when user not a contact on the hakemus`() {
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.sendHakemus(id, null, USERNAME) } throws
                UserNotInContactsException(HakemusFactory.create(id = id))

            post(url).andExpect(status().isForbidden).andExpect(hankeError(HankeError.HAI2012))

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.sendHakemus(id, null, USERNAME)
            }
        }

        @Test
        fun `returns 400 when geometry outside hankealue`() {
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.sendHakemus(id, null, USERNAME) } throws
                HakemusGeometryNotInsideHankeException("Message")

            post(url).andExpect(status().isBadRequest).andExpect(hankeError(HankeError.HAI2007))

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.sendHakemus(id, null, USERNAME)
            }
        }

        @Test
        fun `returns 409 when application has been sent already`() {
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.sendHakemus(id, null, USERNAME) } throws
                HakemusAlreadySentException(id, 414, ApplicationStatus.PENDING)

            post(url).andExpect(status().isConflict).andExpect(hankeError(HankeError.HAI2009))

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.sendHakemus(id, null, USERNAME)
            }
        }

        @Test
        fun `returns 400 when application fails validation`() {
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.sendHakemus(id, null, USERNAME) } throws
                InvalidHakemusDataException(listOf("rockExcavation"))

            post(url)
                .andExpect(status().isBadRequest())
                .andExpect(hankeError(HankeError.HAI2008))
                .andExpect(jsonPath("$.errorPaths.size()").value(1))
                .andExpect(jsonPath("$.errorPaths[0]").value("rockExcavation"))

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.sendHakemus(id, null, USERNAME)
            }
        }

        @Test
        fun `returns hakemus with status when application is sent successfully`() {
            val sentHakemus =
                HakemusFactory.create(
                    alluStatus = ApplicationStatus.PENDING,
                    alluid = 43,
                    applicationIdentifier = "JS001",
                )
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.sendHakemus(id, null, USERNAME) } returns sentHakemus

            val response = post(url).andExpect(status().isOk()).andReturnContent()

            JSONAssert.assertEquals(
                sentHakemus.toResponse().toJsonString(),
                response,
                JSONCompareMode.NON_EXTENSIBLE,
            )
            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.sendHakemus(id, null, USERNAME)
            }
        }

        @ParameterizedTest
        @EnumSource(CustomerType::class, names = ["PERSON", "OTHER"])
        fun `hides the registry key of person customers when it's not null`(tyyppi: CustomerType) {
            val hakemusdata = HakemusFactory.hakemusDataForRegistryKeyTest(tyyppi)
            val hakemus =
                HakemusFactory.create(
                    id = id,
                    hankeTunnus = HANKE_TUNNUS,
                    applicationData = hakemusdata,
                )
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.sendHakemus(id, null, USERNAME) } returns hakemus

            post(url).andExpect(status().isOk).andVerifyRegistryKeys()

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.sendHakemus(id, null, USERNAME)
            }
        }

        @Test
        fun `returns 400 when request body is missing fields`() {
            val paperDecisionReceiver = PaperDecisionReceiverFactory.default
            val request = HakemusSendRequest(paperDecisionReceiver = paperDecisionReceiver)
            val json: ObjectNode = OBJECT_MAPPER.valueToTree(request)
            val child = json.get("paperDecisionReceiver") as ObjectNode
            child.remove("name")

            post(url, json).andExpect(status().isBadRequest())
        }

        @Test
        fun `returns paper decision receiver when request body is present`() {
            val paperDecisionReceiver = PaperDecisionReceiverFactory.default
            val request = HakemusSendRequest(paperDecisionReceiver = paperDecisionReceiver)
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.sendHakemus(id, paperDecisionReceiver, USERNAME) } returns
                HakemusFactory.create(
                    applicationData =
                        HakemusFactory.createJohtoselvityshakemusData(
                            paperDecisionReceiver = paperDecisionReceiver
                        )
                )

            post(url, request)
                .andExpect(status().isOk)
                .andExpect(jsonPath("applicationData.paperDecisionReceiver").exists())
                .andExpect(
                    jsonPath("applicationData.paperDecisionReceiver.name")
                        .value(paperDecisionReceiver.name)
                )
                .andExpect(
                    jsonPath("applicationData.paperDecisionReceiver.streetAddress")
                        .value(paperDecisionReceiver.streetAddress)
                )
                .andExpect(
                    jsonPath("applicationData.paperDecisionReceiver.postalCode")
                        .value(paperDecisionReceiver.postalCode)
                )
                .andExpect(
                    jsonPath("applicationData.paperDecisionReceiver.city")
                        .value(paperDecisionReceiver.city)
                )

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.sendHakemus(id, paperDecisionReceiver, USERNAME)
            }
        }
    }

    @Nested
    inner class DeleteApplication {
        val url = "/hakemukset/$id"

        @Test
        @WithAnonymousUser
        fun `returns 401 when user is unknown`() {
            delete(url).andExpect(status().isUnauthorized).andExpect(hankeError(HankeError.HAI0001))

            verify { hakemusService wasNot Called }
        }

        @Test
        fun `returns 404 when no application or no permission`() {
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } throws HakemusNotFoundException(id)

            delete(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI2001))

            verify { authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name) }
        }

        @ParameterizedTest
        @ValueSource(booleans = [true, false])
        fun `deletes application and returns deletion result when the application is pending`(
            hankeDeleted: Boolean
        ) {
            val expectedResponseBody = HakemusDeletionResultDto(hankeDeleted)
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.deleteWithOrphanGeneratedHankeRemoval(id, USERNAME) } returns
                expectedResponseBody

            delete(url)
                .andExpect(status().isOk)
                .andExpect(content().json(expectedResponseBody.toJsonString()))

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.deleteWithOrphanGeneratedHankeRemoval(id, USERNAME)
            }
        }

        @Test
        fun `returns 409 when application is no longer pending in Allu`() {
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.deleteWithOrphanGeneratedHankeRemoval(id, USERNAME) } throws
                HakemusAlreadyProcessingException(id, 41)

            delete(url).andExpect(status().isConflict).andExpect(hankeError(HankeError.HAI2003))

            verify {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.deleteWithOrphanGeneratedHankeRemoval(id, USERNAME)
            }
        }
    }

    @Nested
    inner class DownloadDecision {
        private val url = "/hakemukset/$id/paatos"

        @Test
        @WithAnonymousUser
        fun `when unknown user should return 401`() {
            get(url).andExpect(status().isUnauthorized)

            verify { hakemusService wasNot Called }
        }

        @Test
        fun `when no application should return 404`() {
            every { authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name) } throws
                HakemusNotFoundException(id)

            get(url)
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("errorCode").value("HAI2001"))
                .andExpect(jsonPath("errorMessage").value("Application not found"))

            verify { authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name) }
        }

        @Test
        fun `when application has no decision should return 404`() {
            every { authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name) } returns true
            every { hakemusService.downloadDecision(id) } throws
                HakemusDecisionNotFoundException("Decision not found in Allu. alluid=23")

            get(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI2006))

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name)
                hakemusService.downloadDecision(id)
            }
        }

        @Test
        fun `when decision exists should return bytes and correct headers`() {
            val applicationIdentifier = "JS230001"
            val pdfBytes = "/fi/hel/haitaton/hanke/decision/fake-decision.pdf".getResourceAsBytes()
            every { authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name) } returns true
            every { hakemusService.downloadDecision(id) } returns
                Pair(applicationIdentifier, pdfBytes)
            every { hakemusService.getById(id) } returns
                HakemusFactory.create(applicationIdentifier = applicationIdentifier)

            get(url, MediaType.APPLICATION_PDF)
                .andExpect(status().isOk)
                .andExpect(
                    MockMvcResultMatchers.header()
                        .string("Content-Disposition", "inline; filename=JS230001.pdf")
                )
                .andExpect(content().bytes(pdfBytes))

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name)
                hakemusService.downloadDecision(id)
                hakemusService.getById(id)
            }
        }

        @Test
        fun `when decision exists should write access to audit log`() {
            val applicationIdentifier = "JS230001"
            val hakemus = HakemusFactory.create(applicationIdentifier = applicationIdentifier)
            val pdfBytes = "/fi/hel/haitaton/hanke/decision/fake-decision.pdf".getResourceAsBytes()
            every { authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name) } returns true
            every { hakemusService.downloadDecision(id) } returns
                Pair(applicationIdentifier, pdfBytes)
            every { hakemusService.getById(id) } returns hakemus

            get(url, MediaType.APPLICATION_PDF).andExpect(status().isOk)

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name)
                hakemusService.downloadDecision(id)
                hakemusService.getById(id)
                disclosureLogService.saveForCableReport(hakemus.toMetadata(), USERNAME)
            }
        }
    }
}

fun ResultActions.andVerifyRegistryKeys() {
    fun registryKeys(role: String, key: String?, hidden: Boolean) = ResultMatcher {
        val prefix = "$.applicationData.$role.customer"
        jsonPath("$prefix.registryKey").value(key).match(it)
        jsonPath("$prefix.registryKeyHidden").value(hidden).match(it)
    }

    andExpect(registryKeys("customerWithContacts", null, true))
        .andExpect(registryKeys("contractorWithContacts", null, false))
        .andExpect(registryKeys("propertyDeveloperWithContacts", "5425233-4", false))
        .andExpect(registryKeys("representativeWithContacts", null, false))
        .andExpect(
            jsonPath("$.applicationData.invoicingCustomer.registryKey").doesNotHaveJsonPath()
        )
        .andExpect(jsonPath("$.applicationData.invoicingCustomer.registryKeyHidden").value(true))
}
