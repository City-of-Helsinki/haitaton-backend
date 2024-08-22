package fi.hel.haitaton.hanke.hakemus

import assertk.all
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import assertk.assertions.prop
import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeErrorDetail
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.andReturnBody
import fi.hel.haitaton.hanke.andReturnContent
import fi.hel.haitaton.hanke.domain.CreateHankeRequest
import fi.hel.haitaton.hanke.factory.ApplicationFactory
import fi.hel.haitaton.hanke.factory.CreateHakemusRequestFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HakemusResponseFactory
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.toUpdateRequest
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withRegistryKey
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withTimes
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withWorkDescription
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.getResourceAsBytes
import fi.hel.haitaton.hanke.hankeError
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.test.USERNAME
import fi.hel.haitaton.hanke.toJsonString
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.verify
import io.mockk.verifySequence
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
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
        fun `when application exists should return it`(applicationType: ApplicationType) {
            every { authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name) } returns true
            every { hakemusService.hakemusResponse(id) } returns
                HakemusResponseFactory.create(
                    applicationType = applicationType,
                    applicationId = id,
                    hankeTunnus = HANKE_TUNNUS
                )

            get(url)
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.hankeTunnus").value(HANKE_TUNNUS))
                .andExpect(jsonPath("$.applicationType").value(applicationType.name))
                .andExpect(
                    jsonPath("$.applicationData.applicationType").value(applicationType.name)
                )

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name)
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
            val cableReportApplicationResponses =
                ApplicationFactory.createApplicationEntities(
                        2,
                        applicationType = ApplicationType.CABLE_REPORT
                    )
                    .map { HankkeenHakemusResponse(it) }
            val excavationNotificationResponses =
                ApplicationFactory.createApplicationEntities(
                        2,
                        applicationType = ApplicationType.EXCAVATION_NOTIFICATION
                    )
                    .map { HankkeenHakemusResponse(it) }
            every { hakemusService.hankkeenHakemuksetResponse(HANKE_TUNNUS) } returns
                HankkeenHakemuksetResponse(
                    cableReportApplicationResponses + excavationNotificationResponses
                )
            every {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
            } returns true

            val response: HankkeenHakemuksetResponse =
                get(url).andExpect(status().isOk).andReturnBody()

            assertThat(response.applications).isNotEmpty()
            assertThat(response)
                .isEqualTo(
                    HankkeenHakemuksetResponse(
                        cableReportApplicationResponses + excavationNotificationResponses
                    )
                )
            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.VIEW.name)
                hakemusService.hankkeenHakemuksetResponse(HANKE_TUNNUS)
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
            val hakemus = HakemusFactory.create(hankeTunnus = hankeTunnus)
            every { hakemusService.create(request, USERNAME) } returns hakemus

            val response: HakemusResponse =
                post(url, request).andExpect(status().isOk).andReturnBody()

            assertThat(response).all {
                prop(HakemusResponse::id).isEqualTo(hakemus.id)
                prop(HakemusResponse::hankeTunnus).isEqualTo(hankeTunnus)
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

            val response = post(url, request).andExpect(status().isOk).andReturnContent()

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
                    JohtoselvityshakemusEntityData::class,
                    JohtoselvityshakemusUpdateRequest::class
                ) // these types are actually compatible but since there are no other application
            // types yet, we use them here

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
                HakemusGeometryException("Invalid geometry")

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
                InvalidHakemusyhteystietoException(
                    HakemusFactory.create(id = id),
                    ApplicationContactType.HAKIJA,
                    null,
                    UUID.randomUUID()
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
                InvalidHakemusyhteyshenkiloException("Invalid contact")

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
            val expectedResponse = HakemusResponseFactory.create(applicationType = applicationType)
            val request = expectedResponse.toUpdateRequest()

            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.updateHakemus(id, request, USERNAME) } returns expectedResponse

            val response = put(url, request).andExpect(status().isOk).andReturnContent()

            JSONAssert.assertEquals(
                expectedResponse.toJsonString(),
                response,
                JSONCompareMode.NON_EXTENSIBLE
            )
            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.updateHakemus(id, request, USERNAME)
            }
        }
    }

    @Nested
    inner class SendApplication {
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
            every { hakemusService.sendHakemus(id, USERNAME) } throws HakemusNotFoundException(id)

            post(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI2001))

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.sendHakemus(id, USERNAME)
            }
        }

        @Test
        fun `returns 403 when user not a contact on the hakemus`() {
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.sendHakemus(id, USERNAME) } throws
                UserNotInContactsException(HakemusFactory.create(id = id))

            post(url).andExpect(status().isForbidden).andExpect(hankeError(HankeError.HAI2012))

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.sendHakemus(id, USERNAME)
            }
        }

        @Test
        fun `returns 400 when geometry outside hankealue`() {
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.sendHakemus(id, USERNAME) } throws
                HakemusGeometryNotInsideHankeException("Message")

            post(url).andExpect(status().isBadRequest).andExpect(hankeError(HankeError.HAI2007))

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.sendHakemus(id, USERNAME)
            }
        }

        @Test
        fun `returns 409 when application has been sent already`() {
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.sendHakemus(id, USERNAME) } throws
                HakemusAlreadySentException(id, 414, ApplicationStatus.PENDING)

            post(url).andExpect(status().isConflict).andExpect(hankeError(HankeError.HAI2009))

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.sendHakemus(id, USERNAME)
            }
        }

        @Test
        fun `returns 400 when application fails validation`() {
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.sendHakemus(id, USERNAME) } throws
                InvalidHakemusDataException(listOf("rockExcavation"))

            post(url)
                .andExpect(status().isBadRequest())
                .andExpect(hankeError(HankeError.HAI2008))
                .andExpect(jsonPath("$.errorPaths.size()").value(1))
                .andExpect(jsonPath("$.errorPaths[0]").value("rockExcavation"))

            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.sendHakemus(id, USERNAME)
            }
        }

        @Test
        fun `returns hakemus with status when application is sent successfully`() {
            val sentHakemus =
                HakemusFactory.create(
                    alluStatus = ApplicationStatus.PENDING,
                    alluid = 43,
                    applicationIdentifier = "JS001"
                )
            every {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { hakemusService.sendHakemus(id, USERNAME) } returns sentHakemus

            val response = post(url).andExpect(status().isOk()).andReturnContent()

            JSONAssert.assertEquals(
                sentHakemus.toResponse().toJsonString(),
                response,
                JSONCompareMode.NON_EXTENSIBLE,
            )
            verifySequence {
                authorizer.authorizeHakemusId(id, PermissionCode.EDIT_APPLICATIONS.name)
                hakemusService.sendHakemus(id, USERNAME)
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
                .andExpect(
                    MockMvcResultMatchers.content().json(expectedResponseBody.toJsonString())
                )

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
                .andExpect(MockMvcResultMatchers.content().bytes(pdfBytes))

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
                disclosureLogService.saveDisclosureLogsForCableReport(
                    hakemus.toMetadata(),
                    USERNAME
                )
            }
        }

        @Test
        fun `when no hanke permission should return 404`() {
            every { authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name) } throws
                HakemusNotFoundException(id)

            get(url).andExpect(status().isNotFound)

            verify { authorizer.authorizeHakemusId(id, PermissionCode.VIEW.name) }
        }
    }
}
