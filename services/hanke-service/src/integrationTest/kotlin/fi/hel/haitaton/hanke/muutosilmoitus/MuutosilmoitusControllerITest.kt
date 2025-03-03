package fi.hel.haitaton.hanke.muutosilmoitus

import assertk.all
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
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
import fi.hel.haitaton.hanke.factory.MuutosilmoitusFactory
import fi.hel.haitaton.hanke.geometria.GeometriatDao
import fi.hel.haitaton.hanke.hakemus.ApplicationType
import fi.hel.haitaton.hanke.hakemus.HakemusAuthorizer
import fi.hel.haitaton.hanke.hakemus.HakemusDataResponse
import fi.hel.haitaton.hanke.hakemus.HakemusGeometryException
import fi.hel.haitaton.hanke.hakemus.HakemusGeometryNotInsideHankeException
import fi.hel.haitaton.hanke.hakemus.HakemusInWrongStatusException
import fi.hel.haitaton.hanke.hakemus.HakemusNotFoundException
import fi.hel.haitaton.hanke.hakemus.InvalidHakemusyhteyshenkiloException
import fi.hel.haitaton.hanke.hakemus.InvalidHakemusyhteystietoException
import fi.hel.haitaton.hanke.hakemus.InvalidHiddenRegistryKey
import fi.hel.haitaton.hanke.hakemus.WrongHakemusTypeException
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
import io.mockk.justRun
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [MuutosilmoitusController::class])
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class MuutosilmoitusControllerITest(
    @Autowired override val mockMvc: MockMvc,
    @Autowired private var muutosilmoitusService: MuutosilmoitusService,
    @Autowired private var hakemusAuthorizer: HakemusAuthorizer,
    @Autowired private var muutosilmoitusAuthorizer: MuutosilmoitusAuthorizer,
    @Autowired private var disclosureLogService: DisclosureLogService,
) : ControllerTest {
    private val hakemusId = 567813L
    private val id = UUID.fromString("c78755ad-4cf7-46ad-9f60-0356da6e41c6")

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(muutosilmoitusService)
    }

    @Nested
    inner class Create {
        private val url = "/hakemukset/$hakemusId/muutosilmoitus"

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
        fun `returns 409 when the hakemus is not in an allowed status`() {
            every {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId,
                    PermissionCode.EDIT_APPLICATIONS.name,
                )
            } returns true
            val hakemus = HakemusFactory.create(hakemusId, alluStatus = ApplicationStatus.HANDLING)
            every { muutosilmoitusService.create(hakemusId, USERNAME) } throws
                HakemusInWrongStatusException(
                    hakemus,
                    ApplicationStatus.HANDLING,
                    listOf(ApplicationStatus.DECISION, ApplicationStatus.OPERATIONAL_CONDITION),
                )

            post(url).andExpect(status().isConflict).andExpect(hankeError(HankeError.HAI2015))

            verifySequence {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId,
                    PermissionCode.EDIT_APPLICATIONS.name,
                )
                muutosilmoitusService.create(hakemusId, USERNAME)
            }
        }

        @Test
        fun `returns 400 when the hakemus is a johtoselvityshakemus`() {
            every {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId,
                    PermissionCode.EDIT_APPLICATIONS.name,
                )
            } returns true
            val hakemus = HakemusFactory.create(hakemusId, alluStatus = ApplicationStatus.HANDLING)
            every { muutosilmoitusService.create(hakemusId, USERNAME) } throws
                WrongHakemusTypeException(
                    hakemus,
                    ApplicationType.CABLE_REPORT,
                    listOf(ApplicationType.EXCAVATION_NOTIFICATION),
                )

            post(url).andExpect(status().isBadRequest).andExpect(hankeError(HankeError.HAI2002))

            verifySequence {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId,
                    PermissionCode.EDIT_APPLICATIONS.name,
                )
                muutosilmoitusService.create(hakemusId, USERNAME)
            }
        }

        @Test
        fun `returns the created muutosilmoitus and writes the access to disclosure logs`() {
            every {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId,
                    PermissionCode.EDIT_APPLICATIONS.name,
                )
            } returns true
            val muutosilmoitus =
                Muutosilmoitus(
                    id,
                    hakemusId,
                    sent = null,
                    HakemusFactory.createKaivuilmoitusData(name = "Muutettu hakemus"),
                )
            every { muutosilmoitusService.create(hakemusId, USERNAME) } returns muutosilmoitus

            val response: MuutosilmoitusResponse =
                post(url).andExpect(status().isOk).andReturnBody()

            assertThat(response).all {
                prop(MuutosilmoitusResponse::id).isEqualTo(id)
                prop(MuutosilmoitusResponse::sent).isNull()
                prop(MuutosilmoitusResponse::applicationData).all {
                    prop(HakemusDataResponse::name).isEqualTo("Muutettu hakemus")
                }
            }
            verifySequence {
                hakemusAuthorizer.authorizeHakemusId(
                    hakemusId,
                    PermissionCode.EDIT_APPLICATIONS.name,
                )
                muutosilmoitusService.create(hakemusId, USERNAME)
                disclosureLogService.saveForMuutosilmoitus(response, USERNAME)
            }
        }
    }

    @Nested
    inner class Update {

        private val url = "/muutosilmoitukset/$id"
        private val request = HakemusUpdateRequestFactory.createFilledKaivuilmoitusUpdateRequest()

        @Test
        @WithAnonymousUser
        fun `returns 401 when user is unknown`() {
            put(url, request)
                .andExpect(status().isUnauthorized)
                .andExpect(hankeError(HankeError.HAI0001))

            verifySequence { muutosilmoitusService wasNot Called }
        }

        @Test
        fun `returns 400 when no request body`() {
            put(url).andExpect(status().isBadRequest)

            verifySequence { muutosilmoitusService wasNot Called }
        }

        @Test
        fun `returns 400 when end date before start date`() {
            val request =
                request.withTimes(
                    startTime = ZonedDateTime.now(),
                    endTime = ZonedDateTime.now().minusDays(1),
                )
            mockAuthorization()
            val response = put(url, request).andExpect(status().isBadRequest).andReturnContent()

            assertThat(response)
                .isEqualTo(HankeErrorDetail(HankeError.HAI2008, listOf("endTime")).toJsonString())
            verifySequence {
                muutosilmoitusAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
                muutosilmoitusService wasNot Called
            }
        }

        @Test
        fun `returns 400 when invalid y-tunnus`() {
            val request = request.withRegistryKey("281192-937W")
            mockAuthorization()
            put(url, request).andExpect(status().isBadRequest)

            verifySequence {
                muutosilmoitusAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
                muutosilmoitusService wasNot Called
            }
        }

        @Test
        fun `returns 400 when missing required data`() {
            val mockErrorPaths = listOf("workDescription")
            val request = request.withWorkDescription(" ")
            mockAuthorization()
            val response = put(url, request).andExpect(status().isBadRequest).andReturnContent()

            assertThat(response)
                .isEqualTo(HankeErrorDetail(HankeError.HAI2008, mockErrorPaths).toJsonString())
            verifySequence {
                muutosilmoitusAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
                muutosilmoitusService wasNot Called
            }
        }

        @Test
        fun `returns 404 when no muutosilmoitus`() {
            every {
                muutosilmoitusAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
            } throws MuutosilmoitusNotFoundException(id)

            put(url, request)
                .andExpect(status().isNotFound)
                .andExpect(hankeError(HankeError.HAI7001))

            verifySequence {
                muutosilmoitusAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
            }
        }

        @Test
        fun `returns 400 when request is not of the same type as the application`() {
            mockAuthorization()
            every { muutosilmoitusService.update(id, request, USERNAME) } throws
                IncompatibleMuutosilmoitusUpdateException(
                    MuutosilmoitusFactory.createEntity(id = id),
                    ApplicationType.EXCAVATION_NOTIFICATION,
                    ApplicationType.CABLE_REPORT,
                )

            put(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI2002))

            verifySequence {
                muutosilmoitusAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
                muutosilmoitusService.update(id, request, USERNAME)
            }
        }

        @Test
        fun `returns 409 when the muutosilmoitus has already been sent`() {
            mockAuthorization()
            every { muutosilmoitusService.update(id, request, USERNAME) } throws
                MuutosilmoitusAlreadySentException(MuutosilmoitusFactory.createEntity(id = id))

            put(url, request)
                .andExpect(status().isConflict)
                .andExpect(hankeError(HankeError.HAI7002))

            verifySequence {
                muutosilmoitusAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
                muutosilmoitusService.update(id, request, USERNAME)
            }
        }

        @Test
        fun `returns 400 when request areas contain invalid geometry`() {
            mockAuthorization()
            every { muutosilmoitusService.update(id, request, USERNAME) } throws
                HakemusGeometryException(GeometriatDao.InvalidDetail("", ""))

            put(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI2005))

            verifySequence {
                muutosilmoitusAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
                muutosilmoitusService.update(id, request, USERNAME)
            }
        }

        @Test
        fun `returns 400 when request areas outside hankealueet`() {
            mockAuthorization()
            every { muutosilmoitusService.update(id, request, USERNAME) } throws
                HakemusGeometryNotInsideHankeException(GeometriaFactory.polygon())

            put(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI2007))

            verifySequence {
                muutosilmoitusAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
                muutosilmoitusService.update(id, request, USERNAME)
            }
        }

        @Test
        fun `returns 400 when request contain invalid customer`() {
            mockAuthorization()
            every { muutosilmoitusService.update(id, request, USERNAME) } throws
                InvalidHakemusyhteystietoException(
                    UUID.fromString("c47d2b42-0c79-410e-a3e4-40e023d98a2f"),
                    UUID.fromString("45efa6f5-6d94-473b-9739-6eb40ec4e7d0"),
                )

            put(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI2010))

            verifySequence {
                muutosilmoitusAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
                muutosilmoitusService.update(id, request, USERNAME)
            }
        }

        @Test
        fun `returns 400 when request contain invalid use of hidden registry key`() {
            mockAuthorization()
            every { muutosilmoitusService.update(id, request, USERNAME) } throws
                InvalidHiddenRegistryKey(
                    "Reason for error",
                    CustomerType.COMPANY,
                    CustomerType.PERSON,
                )

            put(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI2010))

            verifySequence {
                muutosilmoitusAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
                muutosilmoitusService.update(id, request, USERNAME)
            }
        }

        @Test
        fun `returns 400 when request contain invalid contact`() {
            mockAuthorization()
            every { muutosilmoitusService.update(id, request, USERNAME) } throws
                InvalidHakemusyhteyshenkiloException(setOf())

            put(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI2011))

            verifySequence {
                muutosilmoitusAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
                muutosilmoitusService.update(id, request, USERNAME)
            }
        }

        @ParameterizedTest
        @EnumSource(ApplicationType::class)
        fun `returns the updated muutosilmoitus and writes the access to disclosure logs`(
            hakemusType: ApplicationType
        ) {
            val muutosilmoitus = MuutosilmoitusFactory.create(hakemusType = hakemusType)
            val request = HakemusUpdateRequestFactory.createFilledUpdateRequest(hakemusType)
            mockAuthorization()
            every { muutosilmoitusService.update(id, request, USERNAME) } returns muutosilmoitus

            val response = put(url, request).andExpect(status().isOk).andReturnContent()

            val expectedResponse = muutosilmoitus.toResponse()
            JSONAssert.assertEquals(
                expectedResponse.toJsonString(),
                response,
                JSONCompareMode.NON_EXTENSIBLE,
            )
            verifySequence {
                muutosilmoitusAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
                muutosilmoitusService.update(id, request, USERNAME)
                disclosureLogService.saveForMuutosilmoitus(expectedResponse, USERNAME)
            }
        }

        @ParameterizedTest
        @EnumSource(CustomerType::class, names = ["PERSON", "OTHER"])
        fun `hides the registry key of person customers when it's not null`(tyyppi: CustomerType) {
            val hakemusdata = HakemusFactory.hakemusDataForRegistryKeyTest(tyyppi)
            val muutosilmoitus = MuutosilmoitusFactory.create(id = id, hakemusData = hakemusdata)
            mockAuthorization()
            every { muutosilmoitusService.update(id, request, USERNAME) } returns muutosilmoitus

            put(url, request).andExpect(status().isOk).andVerifyRegistryKeys()

            verifySequence {
                muutosilmoitusAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
                muutosilmoitusService.update(id, request, USERNAME)
                disclosureLogService.saveForMuutosilmoitus(muutosilmoitus.toResponse(), USERNAME)
            }
        }
    }

    @Nested
    inner class Delete {
        private val url = "/muutosilmoitukset/$id"

        @Test
        @WithAnonymousUser
        fun `returns 401 when user is unknown`() {
            delete(url).andExpect(status().isUnauthorized).andExpect(hankeError(HankeError.HAI0001))

            verifySequence { muutosilmoitusService wasNot Called }
        }

        @Test
        fun `returns 404 when no muutosilmoitus`() {
            every {
                muutosilmoitusAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
            } throws MuutosilmoitusNotFoundException(id)

            delete(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI7001))

            verifySequence {
                muutosilmoitusAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
            }
        }

        @Test
        fun `returns 404 when no application`() {
            every {
                muutosilmoitusAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
            } throws HakemusNotFoundException(1L)

            delete(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI2001))

            verifySequence {
                muutosilmoitusAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
            }
        }

        @Test
        fun `returns 409 when muutosilmoitus has been sent already`() {
            every {
                muutosilmoitusAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            every { muutosilmoitusService.delete(id, USERNAME) } throws
                MuutosilmoitusAlreadySentException(MuutosilmoitusFactory.create(id = id))

            delete(url).andExpect(status().isConflict).andExpect(hankeError(HankeError.HAI7002))

            verifySequence {
                muutosilmoitusAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
                muutosilmoitusService.delete(id, USERNAME)
            }
        }

        @Test
        fun `returns 204 when muutosilmoitus is deleted`() {
            every {
                muutosilmoitusAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
            } returns true
            justRun { muutosilmoitusService.delete(id, USERNAME) }

            delete(url).andExpect(status().isNoContent).andExpect(content().string(""))

            verifySequence {
                muutosilmoitusAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
                muutosilmoitusService.delete(id, USERNAME)
            }
        }
    }

    private fun mockAuthorization() {
        every {
            muutosilmoitusAuthorizer.authorize(id, PermissionCode.EDIT_APPLICATIONS.name)
        } returns true
    }
}
