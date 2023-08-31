package fi.hel.haitaton.hanke.permissions

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
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

@WebMvcTest(
    HankeKayttajaController::class,
    properties = ["haitaton.features.user-management=true"],
)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("itest")
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

            put(url, PermissionUpdate(listOf(PermissionDto(hankeKayttajaId, Role.HANKEMUOKKAUS))))
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
            val updates = mapOf(hankeKayttajaId to Role.HANKEMUOKKAUS)
            justRun { hankeKayttajaService.updatePermissions(hanke, updates, false, USERNAME) }

            put(url, PermissionUpdate(listOf(PermissionDto(hankeKayttajaId, Role.HANKEMUOKKAUS))))
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
            val updates = mapOf(hankeKayttajaId to Role.HANKEMUOKKAUS)
            justRun { hankeKayttajaService.updatePermissions(hanke, updates, true, USERNAME) }

            put(url, PermissionUpdate(listOf(PermissionDto(hankeKayttajaId, Role.HANKEMUOKKAUS))))
                .andExpect(status().isNoContent)

            verifyCalls(hanke, updates, deleteAdminPermission = true)
        }

        @Test
        fun `Returns forbidden when missing admin permissions`() {
            val (hanke, updates) = setupForException(MissingAdminPermissionException(USERNAME))

            put(url, PermissionUpdate(listOf(PermissionDto(hankeKayttajaId, Role.HANKEMUOKKAUS))))
                .andExpect(status().isForbidden)
                .andExpect(hankeError(HankeError.HAI0005))

            verifyCalls(hanke, updates)
        }

        @Test
        fun `Returns forbidden when changing own permissions`() {
            val (hanke, updates) = setupForException(ChangingOwnPermissionException(USERNAME))

            put(url, PermissionUpdate(listOf(PermissionDto(hankeKayttajaId, Role.HANKEMUOKKAUS))))
                .andExpect(status().isForbidden)
                .andExpect(hankeError(HankeError.HAI4002))

            verifyCalls(hanke, updates)
        }

        @Test
        fun `Returns internal server error if there are users without either permission or tunniste`() {
            val (hanke, updates) =
                setupForException(UsersWithoutRolesException(missingIds = listOf(hankeKayttajaId)))

            put(url, PermissionUpdate(listOf(PermissionDto(hankeKayttajaId, Role.HANKEMUOKKAUS))))
                .andExpect(status().isInternalServerError)
                .andExpect(hankeError(HankeError.HAI4003))

            verifyCalls(hanke, updates)
        }

        @Test
        fun `Returns conflict if there would be no admins remaining`() {
            val (hanke, updates) = setupForException { hanke -> NoAdminRemainingException(hanke) }

            put(url, PermissionUpdate(listOf(PermissionDto(hankeKayttajaId, Role.HANKEMUOKKAUS))))
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

            put(url, PermissionUpdate(listOf(PermissionDto(hankeKayttajaId, Role.HANKEMUOKKAUS))))
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI4001))

            verifyCalls(hanke, updates)
        }

        private fun verifyCalls(
            hanke: Hanke,
            updates: Map<UUID, Role>,
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

        private fun setupForException(ex: Throwable): Pair<Hanke, Map<UUID, Role>> =
            setupForException {
                ex
            }

        private fun setupForException(ex: (Hanke) -> Throwable): Pair<Hanke, Map<UUID, Role>> {
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
            val updates = mapOf(hankeKayttajaId to Role.HANKEMUOKKAUS)
            every { hankeKayttajaService.updatePermissions(hanke, updates, false, USERNAME) } throws
                ex(hanke)

            return Pair(hanke, updates)
        }
    }
}

@WebMvcTest(
    HankeKayttajaController::class,
    properties = ["haitaton.features.user-management=false"],
)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("itest")
@WithMockUser(USERNAME)
class HankeKayttajaControllerFeatureDisabledITest(@Autowired override val mockMvc: MockMvc) :
    ControllerTest {
    private val url = "/hankkeet/$HANKE_TUNNUS/kayttajat"
    private val hankeKayttajaId = UUID.fromString("5d67712f-ea0b-490c-957f-9b30bddb848c")

    @Nested
    inner class UpdatePermissions {
        @Test
        fun `Returns not found when feature disabled`() {
            put(url, PermissionUpdate(listOf(PermissionDto(hankeKayttajaId, Role.HANKEMUOKKAUS))))
                .andExpect(status().isNotFound)
                .andExpect(hankeError(HankeError.HAI0004))
        }
    }
}
