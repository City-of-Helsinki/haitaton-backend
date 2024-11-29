package fi.hel.haitaton.hanke.taydennys

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.prop
import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeErrorDetail
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.allu.CustomerType
import fi.hel.haitaton.hanke.andReturnBody
import fi.hel.haitaton.hanke.andReturnContent
import fi.hel.haitaton.hanke.factory.GeometriaFactory
import fi.hel.haitaton.hanke.factory.HakemusFactory
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withRegistryKey
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withTimes
import fi.hel.haitaton.hanke.factory.HakemusUpdateRequestFactory.withWorkDescription
import fi.hel.haitaton.hanke.factory.TaydennysFactory
import fi.hel.haitaton.hanke.factory.TaydennyspyyntoFactory
import fi.hel.haitaton.hanke.geometria.GeometriatDao
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hakemus.HakemusAuthorizer
import fi.hel.haitaton.hanke.hakemus.HakemusDataResponse
import fi.hel.haitaton.hanke.hakemus.HakemusGeometryException
import fi.hel.haitaton.hanke.hakemus.HakemusGeometryNotInsideHankeException
import fi.hel.haitaton.hanke.hakemus.HakemusInWrongStatusException
import fi.hel.haitaton.hanke.hakemus.HakemusNotFoundException
import fi.hel.haitaton.hanke.hakemus.InvalidHakemusDataException
import fi.hel.haitaton.hanke.hakemus.InvalidHakemusyhteyshenkiloException
import fi.hel.haitaton.hanke.hakemus.InvalidHakemusyhteystietoException
import fi.hel.haitaton.hanke.hakemus.InvalidHiddenRegistryKey
import fi.hel.haitaton.hanke.hakemus.andVerifyRegistryKeys
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
import io.mockk.verifySequence
import java.time.ZonedDateTime
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithAnonymousUser
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [TaydennysController::class])
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class TaydennysControllerITest(@Autowired override val mockMvc: MockMvc) : ControllerTest {
    @Autowired private lateinit var taydennysService: TaydennysService
    @Autowired private lateinit var taydennysAuthorizer: TaydennysAuthorizer
    @Autowired private lateinit var hakemusAuthorizer: HakemusAuthorizer
    @Autowired private lateinit var disclosureLogService: DisclosureLogService

    private val hakemusId = 24050L
    private val id = UUID.fromString("130ee6a4-01c1-4222-8f6f-3baefb133468")

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(hakemusAuthorizer, taydennysService, disclosureLogService)
    }

    @Nested
    inner class Create {
        private val url = "/hakemukset/$hakemusId/taydennys"

        private val taydennys =
            Taydennys(
                UUID.fromString("90b67df3-cd13-4dca-bd30-9dda424d1260"),
                TaydennyspyyntoFactory.DEFAULT_ID,
                1L,
                HakemusFactory.createJohtoselvityshakemusData(name = "Täydennettävä hakemus"),
            )

        @Test
        @WithAnonymousUser
        fun `returns 401 when user is unknown`() {
            post(url).andExpect(status().isUnauthorized).andExpect(hankeError(HankeError.HAI0001))
        }

        @Test
        fun `returns 404 when application does not exist`() {
            every {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId,
                    PermissionCode.EDIT_APPLICATIONS.name,
                )
            } throws HakemusNotFoundException(hakemusId)

            post(url).andExpect(status().isNotFound)

            verifySequence {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId,
                    PermissionCode.EDIT_APPLICATIONS.name,
                )
            }
        }

        @Test
        fun `returns 409 when hakemus doesn't have an open taydennyspyynto`() {
            every {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId,
                    PermissionCode.EDIT_APPLICATIONS.name,
                )
            } returns true
            every { taydennysService.create(hakemusId, USERNAME) } throws
                NoTaydennyspyyntoException(hakemusId)

            post(url).andExpect(status().isConflict).andExpect(hankeError(HankeError.HAI2015))

            verifySequence {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId,
                    PermissionCode.EDIT_APPLICATIONS.name,
                )
                taydennysService.create(hakemusId, USERNAME)
            }
        }

        @Test
        fun `returns 409 when the hakemus is not in WAITING_INFORMATION status`() {
            every {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId,
                    PermissionCode.EDIT_APPLICATIONS.name,
                )
            } returns true
            val hakemus = HakemusFactory.create(hakemusId, alluStatus = ApplicationStatus.HANDLING)
            every { taydennysService.create(hakemusId, USERNAME) } throws
                HakemusInWrongStatusException(
                    hakemus,
                    ApplicationStatus.HANDLING,
                    listOf(ApplicationStatus.WAITING_INFORMATION),
                )

            post(url).andExpect(status().isConflict).andExpect(hankeError(HankeError.HAI2015))

            verifySequence {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId,
                    PermissionCode.EDIT_APPLICATIONS.name,
                )
                taydennysService.create(hakemusId, USERNAME)
            }
        }

        @Test
        fun `returns the created taydennys and writes the access to disclosure logs`() {
            every {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId,
                    PermissionCode.EDIT_APPLICATIONS.name,
                )
            } returns true
            every { taydennysService.create(hakemusId, USERNAME) } returns taydennys

            val response: TaydennysResponse = post(url).andExpect(status().isOk).andReturnBody()

            assertThat(response).all {
                prop(TaydennysResponse::id)
                    .isEqualTo(UUID.fromString("90b67df3-cd13-4dca-bd30-9dda424d1260"))
                prop(TaydennysResponse::applicationData).all {
                    prop(HakemusDataResponse::name).isEqualTo("Täydennettävä hakemus")
                }
            }
            verifySequence {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId,
                    PermissionCode.EDIT_APPLICATIONS.name,
                )
                taydennysService.create(hakemusId, USERNAME)
                disclosureLogService.saveForTaydennys(response, USERNAME)
            }
        }
    }

    @Nested
    inner class Update {
        private val url = "/taydennykset/$id"

        @Test
        @WithAnonymousUser
        fun `returns 401 when unknown user`() {
            put(url, HakemusUpdateRequestFactory.createBlankJohtoselvityshakemusUpdateRequest())
                .andExpect(status().isUnauthorized)

            verifySequence { taydennysService wasNot Called }
        }

        @Test
        fun `returns 400 when no request body`() {
            put(url).andExpect(status().isBadRequest)

            verifySequence { taydennysService wasNot Called }
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
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true

            val response = put(url, request).andExpect(status().isBadRequest).andReturnContent()

            assertThat(response)
                .isEqualTo(HankeErrorDetail(HankeError.HAI2008, listOf("endTime")).toJsonString())
            verifySequence {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
                taydennysService wasNot Called
            }
        }

        @Test
        fun `returns 400 when invalid y-tunnus`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
                    .withRegistryKey("281192-937W")
            every {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true

            put(url, request).andExpect(status().isBadRequest)

            verifySequence {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
                taydennysService wasNot Called
            }
        }

        @Test
        fun `returns 400 when missing required data`() {
            val mockErrorPaths = listOf("workDescription")
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
                    .withWorkDescription(" ")
            every {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true

            val response = put(url, request).andExpect(status().isBadRequest).andReturnContent()

            assertThat(response)
                .isEqualTo(HankeErrorDetail(HankeError.HAI2008, mockErrorPaths).toJsonString())
            verifySequence {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
                taydennysService wasNot Called
            }
        }

        @Test
        fun `returns 404 when no taydennys`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
            every {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
            } throws TaydennysNotFoundException(id)

            put(url, request)
                .andExpect(status().isNotFound)
                .andExpect(hankeError(HankeError.HAI6001))

            verifySequence {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
            }
        }

        @Test
        fun `returns 400 when request is not of the same type as the application`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
            every {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { taydennysService.updateTaydennys(id, request, USERNAME) } throws
                IncompatibleTaydennysUpdateException(
                    TaydennysFactory.createEntity(id = id),
                    ApplicationType.EXCAVATION_NOTIFICATION,
                    ApplicationType.CABLE_REPORT,
                )

            put(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI2002))

            verifySequence {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
                taydennysService.updateTaydennys(id, request, USERNAME)
            }
        }

        @Test
        fun `returns 400 when request areas contain invalid geometry`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
            every {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { taydennysService.updateTaydennys(id, request, USERNAME) } throws
                HakemusGeometryException(GeometriatDao.InvalidDetail("", ""))

            put(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI2005))

            verifySequence {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
                taydennysService.updateTaydennys(id, request, USERNAME)
            }
        }

        @Test
        fun `returns 400 when request areas outside hankealueet`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
            every {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { taydennysService.updateTaydennys(id, request, USERNAME) } throws
                HakemusGeometryNotInsideHankeException(GeometriaFactory.polygon())

            put(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI2007))

            verifySequence {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
                taydennysService.updateTaydennys(id, request, USERNAME)
            }
        }

        @Test
        fun `returns 400 when request contain invalid customer`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
            every {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { taydennysService.updateTaydennys(id, request, USERNAME) } throws
                InvalidHakemusyhteystietoException(
                    UUID.fromString("c47d2b42-0c79-410e-a3e4-40e023d98a2f"),
                    UUID.fromString("45efa6f5-6d94-473b-9739-6eb40ec4e7d0"),
                )

            put(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI2010))

            verifySequence {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
                taydennysService.updateTaydennys(id, request, USERNAME)
            }
        }

        @Test
        fun `returns 400 when request contain invalid use of hidden registry key`() {
            val request = HakemusUpdateRequestFactory.createFilledKaivuilmoitusUpdateRequest()
            every {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { taydennysService.updateTaydennys(id, request, USERNAME) } throws
                InvalidHiddenRegistryKey(
                    "Reason for error",
                    CustomerType.COMPANY,
                    CustomerType.PERSON,
                )

            put(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI2010))

            verifySequence {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
                taydennysService.updateTaydennys(id, request, USERNAME)
            }
        }

        @Test
        fun `returns 400 when request contain invalid contact`() {
            val request =
                HakemusUpdateRequestFactory.createFilledJohtoselvityshakemusUpdateRequest()
            every {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { taydennysService.updateTaydennys(id, request, USERNAME) } throws
                InvalidHakemusyhteyshenkiloException(setOf())

            put(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI2011))

            verifySequence {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
                taydennysService.updateTaydennys(id, request, USERNAME)
            }
        }

        @ParameterizedTest
        @EnumSource(ApplicationType::class)
        fun `returns taydennys when it exists`(hakemusType: ApplicationType) {
            val taydennys = TaydennysFactory.create(hakemusType = hakemusType)
            val request = HakemusUpdateRequestFactory.createFilledUpdateRequest(hakemusType)
            every {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { taydennysService.updateTaydennys(id, request, USERNAME) } returns taydennys

            val response = put(url, request).andExpect(status().isOk).andReturnContent()

            val expectedResponse = taydennys.toResponse()
            JSONAssert.assertEquals(
                expectedResponse.toJsonString(),
                response,
                JSONCompareMode.NON_EXTENSIBLE,
            )
            verifySequence {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
                taydennysService.updateTaydennys(id, request, USERNAME)
                disclosureLogService.saveForTaydennys(expectedResponse, USERNAME)
            }
        }

        @ParameterizedTest
        @EnumSource(CustomerType::class, names = ["PERSON", "OTHER"])
        fun `hides the registry key of person customers when it's not null`(tyyppi: CustomerType) {
            val hakemusdata = HakemusFactory.hakemusDataForRegistryKeyTest(tyyppi)
            val taydennys = TaydennysFactory.create(id = id, hakemusData = hakemusdata)
            val request = HakemusUpdateRequestFactory.createFilledKaivuilmoitusUpdateRequest()
            every {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { taydennysService.updateTaydennys(id, request, USERNAME) } returns taydennys

            put(url, request).andExpect(status().isOk).andVerifyRegistryKeys()

            verifySequence {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
                taydennysService.updateTaydennys(id, request, USERNAME)
                disclosureLogService.saveForTaydennys(taydennys.toResponse(), USERNAME)
            }
        }
    }

    @Nested
    inner class Send {
        private val url = "/taydennykset/$id/laheta"

        @Test
        @WithAnonymousUser
        fun `returns 401 when unknown user`() {
            post(url).andExpect(status().isUnauthorized)
        }

        @Test
        fun `returns 404 when taydennys doesn't exist`() {
            every {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
            } throws TaydennysNotFoundException(id)

            post(url).andExpect(status().isNotFound)
        }

        @Test
        fun `returns 404 when user doesn't have access to the taydennys`() {
            every {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
            } throws HakemusNotFoundException(hakemusId)

            post(url).andExpect(status().isNotFound)
        }

        @Test
        fun `returns 409 when there are no changes in the taydennys`() {
            every {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { taydennysService.sendTaydennys(id, USERNAME) } throws
                NoChangesException(TaydennysFactory.createEntity(), HakemusFactory.create())

            post(url).andExpect(status().isConflict).andExpect(hankeError(HankeError.HAI6002))

            verifySequence {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
                taydennysService.sendTaydennys(id, USERNAME)
            }
        }

        @Test
        fun `returns 409 when the hakemus is in the wrong state`() {
            every {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { taydennysService.sendTaydennys(id, USERNAME) } throws
                HakemusInWrongStatusException(
                    HakemusFactory.create(),
                    ApplicationStatus.HANDLING,
                    listOf(ApplicationStatus.WAITING_INFORMATION),
                )

            post(url).andExpect(status().isConflict).andExpect(hankeError(HankeError.HAI2015))

            verifySequence {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
                taydennysService.sendTaydennys(id, USERNAME)
            }
        }

        @Test
        fun `returns 400 when the data fails validation`() {
            every {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { taydennysService.sendTaydennys(id, USERNAME) } throws
                InvalidHakemusDataException(listOf("rockExcavation"))

            post(url).andExpect(status().isBadRequest).andExpect(hankeError(HankeError.HAI2008))

            verifySequence {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
                taydennysService.sendTaydennys(id, USERNAME)
            }
        }

        @Test
        fun `returns 400 when the geometry is outside hanke geometry`() {
            every {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { taydennysService.sendTaydennys(id, USERNAME) } throws
                HakemusGeometryNotInsideHankeException(GeometriaFactory.polygon())

            post(url).andExpect(status().isBadRequest).andExpect(hankeError(HankeError.HAI2007))

            verifySequence {
                taydennysAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
                taydennysService.sendTaydennys(id, USERNAME)
            }
        }
    }
}
