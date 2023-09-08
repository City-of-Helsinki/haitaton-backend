package fi.hel.haitaton.hanke.permissions

import assertk.all
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.prop
import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.andReturnBody
import fi.hel.haitaton.hanke.domain.Hanke
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import fi.hel.haitaton.hanke.hankeError
import fi.hel.haitaton.hanke.hasSameElementsAs
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.permissions.HankeKayttajaController.Tunnistautuminen
import fi.hel.haitaton.hanke.permissions.PermissionCode.EDIT
import fi.hel.haitaton.hanke.permissions.PermissionCode.VIEW
import io.mockk.Called
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import io.mockk.verifyOrder
import io.mockk.verifySequence
import java.util.UUID
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithAnonymousUser
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

private const val USERNAME = "testUser"
private const val HANKE_TUNNUS = HankeFactory.defaultHankeTunnus

@WebMvcTest(HankeKayttajaController::class)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class HankeKayttajaControllerITest(@Autowired override val mockMvc: MockMvc) : ControllerTest {

    @Autowired private lateinit var hankeKayttajaService: HankeKayttajaService
    @Autowired private lateinit var hankeService: HankeService
    @Autowired private lateinit var permissionService: PermissionService
    @Autowired private lateinit var disclosureLogService: DisclosureLogService

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(hankeKayttajaService, hankeService, permissionService)
    }

    @Nested
    inner class Whoami {
        private val url = "/hankkeet/$HANKE_TUNNUS/whoami"
        private val hankeId = 14
        private val kayttooikeustaso = Kayttooikeustaso.HANKEMUOKKAUS
        private val kayttooikeudet = listOf(VIEW, EDIT)
        private val hankeKayttajaId = UUID.fromString("3d1ff704-1177-4478-ac66-9187b7bbe84a")

        private val permissionEntity =
            PermissionEntity(
                hankeId = hankeId,
                userId = USERNAME,
                kayttooikeustasoEntity = KayttooikeustasoEntity(0, kayttooikeustaso, 1 or 4)
            )

        private val hankeKayttajaEntity =
            HankeKayttajaEntity(
                id = hankeKayttajaId,
                sahkoposti = "Doesn't",
                nimi = "Matter",
                hankeId = hankeId,
                kayttajaTunniste = null,
                permission = null,
            )

        @Test
        fun `Returns 404 if hanke not found`() {
            every { hankeService.getHankeIdOrThrow(HANKE_TUNNUS) } throws
                HankeNotFoundException(HANKE_TUNNUS)

            get(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI1001))

            verify { hankeService.getHankeIdOrThrow(HANKE_TUNNUS) }
        }

        @Test
        fun `Returns 404 if permission not found`() {
            every { hankeService.getHankeIdOrThrow(HANKE_TUNNUS) } returns hankeId
            every { permissionService.findPermission(hankeId, USERNAME) } returns null

            get(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI1001))

            verifySequence {
                hankeService.getHankeIdOrThrow(HANKE_TUNNUS)
                permissionService.findPermission(hankeId, USERNAME)
            }
        }

        @Test
        fun `Returns kayttooikeustaso if hankeKayttaja not found`() {
            every { hankeService.getHankeIdOrThrow(HANKE_TUNNUS) } returns hankeId
            every { permissionService.findPermission(hankeId, USERNAME) } returns permissionEntity
            every { hankeKayttajaService.getKayttajaByUserId(hankeId, USERNAME) } returns null

            val response: WhoamiResponse = get(url).andExpect(status().isOk).andReturnBody()

            assertThat(response).all {
                prop(WhoamiResponse::hankeKayttajaId).isEqualTo(null)
                prop(WhoamiResponse::kayttooikeustaso).isEqualTo(kayttooikeustaso)
                prop(WhoamiResponse::kayttooikeudet).hasSameElementsAs(kayttooikeudet)
            }
            verifySequence {
                hankeService.getHankeIdOrThrow(HANKE_TUNNUS)
                permissionService.findPermission(hankeId, USERNAME)
                hankeKayttajaService.getKayttajaByUserId(hankeId, USERNAME)
            }
        }

        @Test
        fun `Returns kayttooikeustaso and hankeKayttajaId if hankeKayttaja is found`() {
            every { hankeService.getHankeIdOrThrow(HANKE_TUNNUS) } returns hankeId
            every { permissionService.findPermission(hankeId, USERNAME) } returns permissionEntity
            every { hankeKayttajaService.getKayttajaByUserId(hankeId, USERNAME) } returns
                hankeKayttajaEntity

            val response: WhoamiResponse = get(url).andExpect(status().isOk).andReturnBody()

            assertThat(response).all {
                prop(WhoamiResponse::hankeKayttajaId).isEqualTo(hankeKayttajaId)
                prop(WhoamiResponse::kayttooikeustaso).isEqualTo(kayttooikeustaso)
                prop(WhoamiResponse::kayttooikeudet).hasSameElementsAs(kayttooikeudet)
            }
            verifySequence {
                hankeService.getHankeIdOrThrow(HANKE_TUNNUS)
                permissionService.findPermission(hankeId, USERNAME)
                hankeKayttajaService.getKayttajaByUserId(hankeId, USERNAME)
            }
        }
    }

    @Nested
    inner class GetHankeKayttajat {

        @Test
        fun `With valid request returns users of given hanke and logs audit`() {
            val hanke = HankeFactory.create()
            val testData = HankeKayttajaFactory.generateHankeKayttajat()
            every { hankeService.findHankeOrThrow(HANKE_TUNNUS) } returns hanke
            justRun { permissionService.verifyHankeUserAuthorization(USERNAME, hanke, VIEW) }
            every { hankeKayttajaService.getKayttajatByHankeId(hanke.id!!) } returns testData

            val response: HankeKayttajaResponse =
                getHankeKayttajat().andExpect(status().isOk).andReturnBody()

            assertThat(response.kayttajat).hasSize(3)
            with(response.kayttajat.first()) {
                assertThat(id).isNotNull()
                assertThat(nimi).isEqualTo("test name1")
                assertThat(sahkoposti).isEqualTo("email.1.address.com")
                assertThat(tunnistautunut).isEqualTo(false)
            }
            assertThat(response.kayttajat).hasSameElementsAs(testData)
            verifyOrder {
                hankeService.findHankeOrThrow(HANKE_TUNNUS)
                permissionService.verifyHankeUserAuthorization(USERNAME, hanke, VIEW)
                hankeKayttajaService.getKayttajatByHankeId(hanke.id!!)
                disclosureLogService.saveDisclosureLogsForHankeKayttajat(
                    response.kayttajat,
                    USERNAME
                )
            }
        }

        @Test
        fun `getHankeKayttajat when no permission for hanke returns not found`() {
            val hanke = HankeFactory.create()
            every { hankeService.findHankeOrThrow(HANKE_TUNNUS) } returns hanke
            every { permissionService.verifyHankeUserAuthorization(USERNAME, hanke, VIEW) } throws
                HankeNotFoundException(HANKE_TUNNUS)

            getHankeKayttajat().andExpect(status().isNotFound)

            verifyOrder {
                hankeService.findHankeOrThrow(HANKE_TUNNUS)
                permissionService.verifyHankeUserAuthorization(USERNAME, hanke, VIEW)
            }
            verify { hankeKayttajaService wasNot Called }
        }

        @Test
        @WithAnonymousUser
        fun `When unauthorized token returns 401 `() {
            getHankeKayttajat().andExpect(status().isUnauthorized)
        }

        private fun getHankeKayttajat(): ResultActions = get("/hankkeet/$HANKE_TUNNUS/kayttajat")
    }

    @Nested
    inner class UpdatePermissions {
        private val url = "/hankkeet/$HANKE_TUNNUS/kayttajat"
        private val hankeKayttajaId = UUID.fromString("5d67712f-ea0b-490c-957f-9b30bddb848c")

        @Test
        @WithAnonymousUser
        fun `Returns 401 when unauthorized token`() {
            put(url).andExpect(status().isUnauthorized)
        }

        @Test
        fun `Returns not found when no permission for hanke`() {
            val hanke = HankeFactory.create()
            every { hankeService.findHankeOrThrow(HANKE_TUNNUS) } returns hanke
            every {
                permissionService.verifyHankeUserAuthorization(
                    USERNAME,
                    hanke,
                    PermissionCode.MODIFY_EDIT_PERMISSIONS
                )
            } throws HankeNotFoundException(HANKE_TUNNUS)

            put(
                    url,
                    PermissionUpdate(
                        listOf(PermissionDto(hankeKayttajaId, Kayttooikeustaso.HANKEMUOKKAUS))
                    )
                )
                .andExpect(status().isNotFound)

            verifySequence {
                hankeService.findHankeOrThrow(HANKE_TUNNUS)
                permissionService.verifyHankeUserAuthorization(
                    USERNAME,
                    hanke,
                    PermissionCode.MODIFY_EDIT_PERMISSIONS
                )
            }
            verify { hankeKayttajaService wasNot Called }
        }

        @Test
        fun `Returns 204 on success`() {
            val hanke = HankeFactory.create()
            every { hankeService.findHankeOrThrow(HANKE_TUNNUS) } returns hanke
            justRun {
                permissionService.verifyHankeUserAuthorization(
                    USERNAME,
                    hanke,
                    PermissionCode.MODIFY_EDIT_PERMISSIONS
                )
            }
            every {
                permissionService.hasPermission(
                    hanke.id!!,
                    USERNAME,
                    PermissionCode.MODIFY_DELETE_PERMISSIONS
                )
            } returns false
            val updates = mapOf(hankeKayttajaId to Kayttooikeustaso.HANKEMUOKKAUS)
            justRun { hankeKayttajaService.updatePermissions(hanke, updates, false, USERNAME) }

            put(
                    url,
                    PermissionUpdate(
                        listOf(PermissionDto(hankeKayttajaId, Kayttooikeustaso.HANKEMUOKKAUS))
                    )
                )
                .andExpect(status().isNoContent)
                .andExpect(content().string(""))

            verifyCalls(hanke, updates)
        }

        @Test
        fun `Calls service with admin permission when user has them`() {
            val hanke = HankeFactory.create()
            every { hankeService.findHankeOrThrow(HANKE_TUNNUS) } returns hanke
            justRun {
                permissionService.verifyHankeUserAuthorization(
                    USERNAME,
                    hanke,
                    PermissionCode.MODIFY_EDIT_PERMISSIONS
                )
            }
            every {
                permissionService.hasPermission(
                    hanke.id!!,
                    USERNAME,
                    PermissionCode.MODIFY_DELETE_PERMISSIONS
                )
            } returns true
            val updates = mapOf(hankeKayttajaId to Kayttooikeustaso.HANKEMUOKKAUS)
            justRun { hankeKayttajaService.updatePermissions(hanke, updates, true, USERNAME) }

            put(
                    url,
                    PermissionUpdate(
                        listOf(PermissionDto(hankeKayttajaId, Kayttooikeustaso.HANKEMUOKKAUS))
                    )
                )
                .andExpect(status().isNoContent)

            verifyCalls(hanke, updates, deleteAdminPermission = true)
        }

        @Test
        fun `Returns forbidden when missing admin permissions`() {
            val (hanke, updates) = setupForException(MissingAdminPermissionException(USERNAME))

            put(
                    url,
                    PermissionUpdate(
                        listOf(PermissionDto(hankeKayttajaId, Kayttooikeustaso.HANKEMUOKKAUS))
                    )
                )
                .andExpect(status().isForbidden)
                .andExpect(hankeError(HankeError.HAI0005))

            verifyCalls(hanke, updates)
        }

        @Test
        fun `Returns forbidden when changing own permissions`() {
            val (hanke, updates) = setupForException(ChangingOwnPermissionException(USERNAME))

            put(
                    url,
                    PermissionUpdate(
                        listOf(PermissionDto(hankeKayttajaId, Kayttooikeustaso.HANKEMUOKKAUS))
                    )
                )
                .andExpect(status().isForbidden)
                .andExpect(hankeError(HankeError.HAI4002))

            verifyCalls(hanke, updates)
        }

        @Test
        fun `Returns internal server error if there are users without either permission or tunniste`() {
            val (hanke, updates) =
                setupForException(
                    UsersWithoutKayttooikeustasoException(missingIds = listOf(hankeKayttajaId))
                )

            put(
                    url,
                    PermissionUpdate(
                        listOf(PermissionDto(hankeKayttajaId, Kayttooikeustaso.HANKEMUOKKAUS))
                    )
                )
                .andExpect(status().isInternalServerError)
                .andExpect(hankeError(HankeError.HAI4003))

            verifyCalls(hanke, updates)
        }

        @Test
        fun `Returns conflict if there would be no admins remaining`() {
            val (hanke, updates) = setupForException { hanke -> NoAdminRemainingException(hanke) }

            put(
                    url,
                    PermissionUpdate(
                        listOf(PermissionDto(hankeKayttajaId, Kayttooikeustaso.HANKEMUOKKAUS))
                    )
                )
                .andExpect(status().isConflict)
                .andExpect(hankeError(HankeError.HAI4003))

            verifyCalls(hanke, updates)
        }

        @Test
        fun `Returns bad request if there would be no admins remaining`() {
            val (hanke, updates) =
                setupForException { hanke ->
                    HankeKayttajatNotFoundException(listOf(hankeKayttajaId), hanke)
                }

            put(
                    url,
                    PermissionUpdate(
                        listOf(PermissionDto(hankeKayttajaId, Kayttooikeustaso.HANKEMUOKKAUS))
                    )
                )
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI4001))

            verifyCalls(hanke, updates)
        }

        private fun verifyCalls(
            hanke: Hanke,
            updates: Map<UUID, Kayttooikeustaso>,
            deleteAdminPermission: Boolean = false,
        ) {
            verifySequence {
                hankeService.findHankeOrThrow(HANKE_TUNNUS)
                permissionService.verifyHankeUserAuthorization(
                    USERNAME,
                    hanke,
                    PermissionCode.MODIFY_EDIT_PERMISSIONS
                )
                permissionService.hasPermission(
                    hanke.id!!,
                    USERNAME,
                    PermissionCode.MODIFY_DELETE_PERMISSIONS
                )
                hankeKayttajaService.updatePermissions(
                    hanke,
                    updates,
                    deleteAdminPermission,
                    USERNAME
                )
            }
        }

        private fun setupForException(ex: Throwable): Pair<Hanke, Map<UUID, Kayttooikeustaso>> =
            setupForException {
                ex
            }

        private fun setupForException(
            ex: (Hanke) -> Throwable
        ): Pair<Hanke, Map<UUID, Kayttooikeustaso>> {
            val hanke = HankeFactory.create()
            every { hankeService.findHankeOrThrow(HANKE_TUNNUS) } returns hanke
            justRun {
                permissionService.verifyHankeUserAuthorization(
                    USERNAME,
                    hanke,
                    PermissionCode.MODIFY_EDIT_PERMISSIONS
                )
            }
            every {
                permissionService.hasPermission(
                    hanke.id!!,
                    USERNAME,
                    PermissionCode.MODIFY_DELETE_PERMISSIONS
                )
            } returns false
            val updates = mapOf(hankeKayttajaId to Kayttooikeustaso.HANKEMUOKKAUS)
            every { hankeKayttajaService.updatePermissions(hanke, updates, false, USERNAME) } throws
                ex(hanke)

            return Pair(hanke, updates)
        }
    }

    @Nested
    inner class IdentifyUser {
        private val url = "/kayttajat"
        private val tunniste = "r5cmC0BmJaSX5Q6WA981ow8j"
        private val tunnisteId = UUID.fromString("827fe492-2add-4d87-9564-049f963c1d86")
        private val kayttajaId = UUID.fromString("ee239f7a-c5bb-4462-b9a5-3695eb410086")
        private val permissionId = 156

        @Test
        fun `Returns 204 on success`() {
            justRun { hankeKayttajaService.createPermissionFromToken(USERNAME, tunniste) }

            post(url, Tunnistautuminen(tunniste))
                .andExpect(status().isNoContent)
                .andExpect(content().string(""))

            verify { hankeKayttajaService.createPermissionFromToken(USERNAME, tunniste) }
        }

        @Test
        fun `Returns 404 when tunniste not found`() {
            every { hankeKayttajaService.createPermissionFromToken(USERNAME, tunniste) } throws
                TunnisteNotFoundException(USERNAME, tunniste)

            post(url, Tunnistautuminen(tunniste))
                .andExpect(status().isNotFound)
                .andExpect(hankeError(HankeError.HAI4004))

            verify { hankeKayttajaService.createPermissionFromToken(USERNAME, tunniste) }
        }

        @Test
        fun `Returns 500 when tunniste is orphaned`() {
            every { hankeKayttajaService.createPermissionFromToken(USERNAME, tunniste) } throws
                OrphanedTunnisteException(USERNAME, tunnisteId)

            post(url, Tunnistautuminen(tunniste))
                .andExpect(status().isInternalServerError)
                .andExpect(hankeError(HankeError.HAI4001))

            verify { hankeKayttajaService.createPermissionFromToken(USERNAME, tunniste) }
        }

        @Test
        fun `Returns 409 when user already has a permission`() {
            every { hankeKayttajaService.createPermissionFromToken(USERNAME, tunniste) } throws
                UserAlreadyHasPermissionException(USERNAME, tunnisteId, permissionId)

            post(url, Tunnistautuminen(tunniste))
                .andExpect(status().isConflict)
                .andExpect(hankeError(HankeError.HAI4003))

            verify { hankeKayttajaService.createPermissionFromToken(USERNAME, tunniste) }
        }

        @Test
        fun `Returns 409 when other user already has a permission for the hanke kayttaja`() {
            every { hankeKayttajaService.createPermissionFromToken(USERNAME, tunniste) } throws
                PermissionAlreadyExistsException(USERNAME, "Other user", kayttajaId, permissionId)

            post(url, Tunnistautuminen(tunniste))
                .andExpect(status().isConflict)
                .andExpect(hankeError(HankeError.HAI4003))

            verify { hankeKayttajaService.createPermissionFromToken(USERNAME, tunniste) }
        }
    }
}

@WebMvcTest(
    HankeKayttajaController::class,
    properties = ["haitaton.features.user-management=false"],
)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class HankeKayttajaControllerFeatureDisabledITest(@Autowired override val mockMvc: MockMvc) :
    ControllerTest {
    private val url = "/hankkeet/$HANKE_TUNNUS/kayttajat"
    private val hankeKayttajaId = UUID.fromString("5d67712f-ea0b-490c-957f-9b30bddb848c")

    @Nested
    inner class UpdatePermissions {
        @Test
        fun `Returns not found when feature disabled`() {
            put(
                    url,
                    PermissionUpdate(
                        listOf(PermissionDto(hankeKayttajaId, Kayttooikeustaso.HANKEMUOKKAUS))
                    )
                )
                .andExpect(status().isNotFound)
                .andExpect(hankeError(HankeError.HAI0004))
        }
    }
}
