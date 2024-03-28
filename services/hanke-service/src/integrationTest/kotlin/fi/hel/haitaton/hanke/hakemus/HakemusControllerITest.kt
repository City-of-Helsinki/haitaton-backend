package fi.hel.haitaton.hanke.hakemus

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeErrorDetail
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.andReturnBody
import fi.hel.haitaton.hanke.application.ApplicationAlreadySentException
import fi.hel.haitaton.hanke.application.ApplicationAuthorizer
import fi.hel.haitaton.hanke.application.ApplicationContactType
import fi.hel.haitaton.hanke.application.ApplicationGeometryException
import fi.hel.haitaton.hanke.application.ApplicationGeometryNotInsideHankeException
import fi.hel.haitaton.hanke.application.ApplicationNotFoundException
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.domain.CreateHankeRequest
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HakemusResponseFactory
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.toUpdateRequest
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withRegistryKey
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withTimes
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withWorkDescription
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.hankeError
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.test.USERNAME
import fi.hel.haitaton.hanke.toJsonString
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verifySequence
import java.nio.charset.StandardCharsets
import java.time.ZonedDateTime
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithAnonymousUser
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private const val HANKE_TUNNUS = "HAI-1234"

@WebMvcTest(
    controllers = [HakemusController::class],
    properties = ["haitaton.features.user-management=true"]
)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class HakemusControllerITest(@Autowired override val mockMvc: MockMvc) : ControllerTest {

    @Autowired private lateinit var hakemusService: HakemusService
    @Autowired private lateinit var hankeService: HankeService
    @Autowired private lateinit var authorizer: ApplicationAuthorizer

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
            every { authorizer.authorizeApplicationId(id, PermissionCode.VIEW.name) } throws
                ApplicationNotFoundException(id)

            get(url).andExpect(status().isNotFound)

            verifySequence { authorizer.authorizeApplicationId(id, PermissionCode.VIEW.name) }
        }

        @Test
        fun `when application exists should return it`() {
            every { authorizer.authorizeApplicationId(id, PermissionCode.VIEW.name) } returns true
            every { hakemusService.hakemusResponse(id) } returns
                HakemusResponseFactory.create(applicationId = id, hankeTunnus = HANKE_TUNNUS)

            get(url)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.hankeTunnus").value(HANKE_TUNNUS))
                .andExpect(jsonPath("$.applicationType").value("CABLE_REPORT"))
                .andExpect(jsonPath("$.applicationData.applicationType").value("CABLE_REPORT"))

            verifySequence {
                authorizer.authorizeApplicationId(id, PermissionCode.VIEW.name)
                hakemusService.hakemusResponse(id)
            }
        }
    }

    @Nested
    inner class GetHankkeenHakemukset {
        private val url = "/hankkeet/$HANKE_TUNNUS/hakemukset"

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
            every { hakemusService.hankkeenHakemuksetResponse(HANKE_TUNNUS) } throws
                HankeNotFoundException(HANKE_TUNNUS)

            get(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI1001))

            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
                hakemusService.hankkeenHakemuksetResponse(HANKE_TUNNUS)
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
            every { hakemusService.hankkeenHakemuksetResponse(HANKE_TUNNUS) } returns
                HankkeenHakemuksetResponse(emptyList())
            every {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
            } returns true

            val response: HankkeenHakemuksetResponse =
                get(url).andExpect(status().isOk).andReturnBody()

            assertThat(response.applications).isEmpty()
            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
                hakemusService.hankkeenHakemuksetResponse(HANKE_TUNNUS)
            }
        }

        @Test
        fun `With known hanketunnus return applications`() {
            val applicationResponses =
                ApplicationFactory.createApplicationEntities(5).map { HankkeenHakemusResponse(it) }
            every { hakemusService.hankkeenHakemuksetResponse(HANKE_TUNNUS) } returns
                HankkeenHakemuksetResponse(applicationResponses)
            every {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
            } returns true

            val response: HankkeenHakemuksetResponse =
                get(url).andExpect(status().isOk).andReturnBody()

            assertThat(response.applications).isNotEmpty()
            assertThat(response).isEqualTo(HankkeenHakemuksetResponse(applicationResponses))
            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
                hakemusService.hankkeenHakemuksetResponse(HANKE_TUNNUS)
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
                    HankeFactory.DEFAULT_HANKE_PERUSTAJA.copy(sahkoposti = "")
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

            val response =
                post(url, request)
                    .andExpect(status().isOk)
                    .andReturn()
                    .response
                    .getContentAsString(StandardCharsets.UTF_8)

            val expectedResponse = hakemus.toResponse()
            JSONAssert.assertEquals(
                expectedResponse.toJsonString(),
                response,
                JSONCompareMode.NON_EXTENSIBLE
            )
            verifySequence { hankeService.generateHankeWithJohtoselvityshakemus(request, any()) }
        }
    }

    @Nested
    inner class UpdateApplication {
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
                        endTime = ZonedDateTime.now().minusDays(1)
                    )
            every {
                authorizer.authorizeApplicationId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true

            val result = put(url, request).andExpect(status().isBadRequest).andReturn()

            assertThat(result.response.contentAsString)
                .isEqualTo(HankeErrorDetail(HankeError.HAI2008, listOf("endTime")).toJsonString())
            verifySequence {
                authorizer.authorizeApplicationId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService wasNot Called
            }
        }

        @Test
        fun `returns 400 when invalid y-tunnus`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
                    .withRegistryKey("281192-937W")
            every {
                authorizer.authorizeApplicationId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true

            put(url, request).andExpect(status().isBadRequest)

            verifySequence {
                authorizer.authorizeApplicationId(id, PermissionCode.EDIT_APPLICATIONS.name)
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
                authorizer.authorizeApplicationId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true

            val response = put(url, request).andExpect(status().isBadRequest).andReturn()

            assertThat(response.response.contentAsString)
                .isEqualTo(HankeErrorDetail(HankeError.HAI2008, mockErrorPaths).toJsonString())
            verifySequence {
                authorizer.authorizeApplicationId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService wasNot Called
            }
        }

        @Test
        fun `returns 404 when no application`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
            every {
                authorizer.authorizeApplicationId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } throws ApplicationNotFoundException(id)

            put(url, request)
                .andExpect(status().isNotFound)
                .andExpect(hankeError(HankeError.HAI2001))

            verifySequence {
                authorizer.authorizeApplicationId(id, PermissionCode.EDIT_APPLICATIONS.name)
            }
        }

        @Test
        fun `returns 409 when application sent to Allu`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
            every {
                authorizer.authorizeApplicationId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.updateHakemus(id, request, USERNAME) } throws
                ApplicationAlreadySentException(id, 21)

            put(url, request)
                .andExpect(status().isConflict)
                .andExpect(hankeError(HankeError.HAI2009))

            verifySequence {
                authorizer.authorizeApplicationId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.updateHakemus(id, request, USERNAME)
            }
        }

        @Test
        fun `returns 400 when request is not of the same type as the application`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
            every {
                authorizer.authorizeApplicationId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.updateHakemus(id, request, USERNAME) } throws
                IncompatibleHakemusUpdateRequestException(
                    id,
                    CableReportApplicationData::class,
                    JohtoselvityshakemusUpdateRequest::class
                ) // these types are actually compatible but since there are no other application
            // types yet, we use them here

            put(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI2002))

            verifySequence {
                authorizer.authorizeApplicationId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.updateHakemus(id, request, USERNAME)
            }
        }

        @Test
        fun `returns 400 when request areas contain invalid geometry`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
            every {
                authorizer.authorizeApplicationId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.updateHakemus(id, request, USERNAME) } throws
                ApplicationGeometryException("Invalid geometry")

            put(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI2005))

            verifySequence {
                authorizer.authorizeApplicationId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.updateHakemus(id, request, USERNAME)
            }
        }

        @Test
        fun `returns 400 when request areas outside hankealueet`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
            every {
                authorizer.authorizeApplicationId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.updateHakemus(id, request, USERNAME) } throws
                ApplicationGeometryNotInsideHankeException("Message")

            put(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI2007))

            verifySequence {
                authorizer.authorizeApplicationId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.updateHakemus(id, request, USERNAME)
            }
        }

        @Test
        fun `returns 400 when request contain invalid customer`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
            every {
                authorizer.authorizeApplicationId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.updateHakemus(id, request, USERNAME) } throws
                InvalidHakemusyhteystietoException(
                    id,
                    ApplicationContactType.HAKIJA,
                    null,
                    UUID.randomUUID()
                )

            put(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI2010))

            verifySequence {
                authorizer.authorizeApplicationId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.updateHakemus(id, request, USERNAME)
            }
        }

        @Test
        fun `returns 400 when request contain invalid contact`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
            every {
                authorizer.authorizeApplicationId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.updateHakemus(id, request, USERNAME) } throws
                InvalidHakemusyhteyshenkiloException("Invalid contact")

            put(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI2011))

            verifySequence {
                authorizer.authorizeApplicationId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.updateHakemus(id, request, USERNAME)
            }
        }

        @Test
        fun `returns application when it exists`() {
            val expectedResponse = HakemusResponseFactory.create()
            val request = expectedResponse.toUpdateRequest()

            every {
                authorizer.authorizeApplicationId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.updateHakemus(id, request, USERNAME) } returns expectedResponse

            val response =
                put(url, request)
                    .andExpect(status().isOk)
                    .andReturn()
                    .response
                    .getContentAsString(StandardCharsets.UTF_8)

            JSONAssert.assertEquals(
                expectedResponse.toJsonString(),
                response,
                JSONCompareMode.NON_EXTENSIBLE
            )
            verifySequence {
                authorizer.authorizeApplicationId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.updateHakemus(id, request, USERNAME)
            }
        }
    }
}
