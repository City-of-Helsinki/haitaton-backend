package fi.hel.haitaton.hanke.permissions

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import assertk.assertions.prop
import fi.hel.haitaton.hanke.ContactType
import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
import fi.hel.haitaton.hanke.allu.ApplicationStatus
import fi.hel.haitaton.hanke.andReturnBody
import fi.hel.haitaton.hanke.factory.HankeFactory
import fi.hel.haitaton.hanke.factory.HankeIdentifierFactory
import fi.hel.haitaton.hanke.factory.HankeKayttajaFactory
import fi.hel.haitaton.hanke.factory.TestHankeIdentifier
import fi.hel.haitaton.hanke.factory.identifier
import fi.hel.haitaton.hanke.hankeError
import fi.hel.haitaton.hanke.hasSameElementsAs
import fi.hel.haitaton.hanke.logging.DisclosureLogService
import fi.hel.haitaton.hanke.permissions.HankeKayttajaController.Tunnistautuminen
import fi.hel.haitaton.hanke.permissions.HankeKayttajaController.TunnistautuminenResponse
import fi.hel.haitaton.hanke.permissions.HankekayttajaDeleteService.DeleteInfo
import fi.hel.haitaton.hanke.permissions.PermissionCode.DELETE_USER
import fi.hel.haitaton.hanke.permissions.PermissionCode.EDIT
import fi.hel.haitaton.hanke.permissions.PermissionCode.MODIFY_EDIT_PERMISSIONS
import fi.hel.haitaton.hanke.permissions.PermissionCode.MODIFY_USER
import fi.hel.haitaton.hanke.permissions.PermissionCode.RESEND_INVITATION
import fi.hel.haitaton.hanke.permissions.PermissionCode.VIEW
import fi.hel.haitaton.hanke.profiili.VerifiedNameNotFound
import fi.hel.haitaton.hanke.test.USERNAME
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

private const val HANKE_TUNNUS = HankeFactory.defaultHankeTunnus

@WebMvcTest(HankeKayttajaController::class)
@Import(IntegrationTestConfiguration::class)
@ActiveProfiles("test")
@WithMockUser(USERNAME)
class HankeKayttajaControllerITest(
    @Autowired override val mockMvc: MockMvc,
    @Autowired private val hankeKayttajaService: HankeKayttajaService,
    @Autowired private val deleteService: HankekayttajaDeleteService,
    @Autowired private val hankeService: HankeService,
    @Autowired private val permissionService: PermissionService,
    @Autowired private val disclosureLogService: DisclosureLogService,
    @Autowired private val authorizer: HankeKayttajaAuthorizer,
) : ControllerTest {

    @BeforeEach
    fun clearMocks() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(hankeKayttajaService, permissionService, disclosureLogService, authorizer)
    }

    @Nested
    inner class Whoami {
        private val url = "/hankkeet/$HANKE_TUNNUS/whoami"
        private val hankeId = 14
        private val hankeIdentifier = TestHankeIdentifier(14, HANKE_TUNNUS)
        private val kayttooikeustaso = Kayttooikeustaso.HANKEMUOKKAUS
        private val kayttooikeudet = listOf(VIEW, EDIT)
        private val hankeKayttajaId = UUID.fromString("3d1ff704-1177-4478-ac66-9187b7bbe84a")

        private val permissionEntity =
            PermissionEntity(
                hankeId = hankeId,
                userId = USERNAME,
                kayttooikeustasoEntity = KayttooikeustasoEntity(0, kayttooikeustaso, 1 or 4),
            )

        private val hankeKayttajaEntity =
            HankekayttajaEntity(
                id = hankeKayttajaId,
                sahkoposti = "some@email.fi",
                etunimi = "Test",
                sukunimi = "Person",
                puhelin = "0401234567",
                hankeId = hankeId,
                kayttajakutsu = null,
                permission = null,
            )

        @Test
        fun `Returns 404 if hanke not found`() {
            every { authorizer.authorizeHankeTunnus(HANKE_TUNNUS, VIEW.name) } throws
                HankeNotFoundException(HANKE_TUNNUS)

            get(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI1001))

            verify { authorizer.authorizeHankeTunnus(HANKE_TUNNUS, VIEW.name) }
        }

        @Test
        fun `Returns kayttooikeustaso if hankeKayttaja not found`() {
            every { authorizer.authorizeHankeTunnus(HANKE_TUNNUS, VIEW.name) } returns true
            every { hankeService.findIdentifier(HANKE_TUNNUS) } returns hankeIdentifier
            every { permissionService.findPermission(hankeId, USERNAME) } returns permissionEntity
            every { hankeKayttajaService.getKayttajaByUserId(hankeId, USERNAME) } returns null

            val response: WhoamiResponse = get(url).andExpect(status().isOk).andReturnBody()

            assertThat(response).all {
                prop(WhoamiResponse::hankeKayttajaId).isEqualTo(null)
                prop(WhoamiResponse::kayttooikeustaso).isEqualTo(kayttooikeustaso)
                prop(WhoamiResponse::kayttooikeudet).hasSameElementsAs(kayttooikeudet)
            }
            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, VIEW.name)
                permissionService.findPermission(hankeId, USERNAME)
                hankeKayttajaService.getKayttajaByUserId(hankeId, USERNAME)
            }
        }

        @Test
        fun `Returns kayttooikeustaso and hankeKayttajaId if hankeKayttaja is found`() {
            every { authorizer.authorizeHankeTunnus(HANKE_TUNNUS, VIEW.name) } returns true
            every { hankeService.findIdentifier(HANKE_TUNNUS) } returns hankeIdentifier
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
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, VIEW.name)
                permissionService.findPermission(hankeId, USERNAME)
                hankeKayttajaService.getKayttajaByUserId(hankeId, USERNAME)
            }
        }
    }

    @Nested
    inner class WhoAmIByHanke {
        private val url = "/my-permissions"

        private val tunnus1 = "HAI23-1"
        private val kayttaja1 = UUID.fromString("93473a95-1520-428c-b203-5d770fef78aa")

        private val tunnus2 = "HAI23-2"

        private val hankePermissions =
            listOf(
                HankePermission(
                    hankeTunnus = tunnus1,
                    hankeKayttajaId = kayttaja1,
                    kayttooikeustaso = Kayttooikeustaso.KAIKKI_OIKEUDET,
                    permissionCode = PermissionCode.entries.sumOf { it.code },
                ),
                HankePermission(
                    hankeTunnus = tunnus2,
                    hankeKayttajaId = null,
                    kayttooikeustaso = Kayttooikeustaso.KATSELUOIKEUS,
                    permissionCode = VIEW.code,
                ),
            )

        @Test
        fun `Should return kayttooikeustaso and hankeKayttajaId if hankeKayttaja is found`() {
            every { permissionService.permissionsByHanke(USERNAME) } returns hankePermissions

            val response: Map<String, WhoamiResponse> =
                get(url).andExpect(status().isOk).andReturnBody()

            assertThat(response).hasSize(2)
            assertThat(response[tunnus1]).isNotNull().all {
                prop(WhoamiResponse::hankeKayttajaId).isEqualTo(kayttaja1)
                prop(WhoamiResponse::kayttooikeustaso).isEqualTo(Kayttooikeustaso.KAIKKI_OIKEUDET)
                prop(WhoamiResponse::kayttooikeudet).hasSameElementsAs(PermissionCode.entries)
            }
            assertThat(response[tunnus2]).isNotNull().all {
                prop(WhoamiResponse::hankeKayttajaId).isNull()
                prop(WhoamiResponse::kayttooikeustaso).isEqualTo(Kayttooikeustaso.KATSELUOIKEUS)
                prop(WhoamiResponse::kayttooikeudet).hasSameElementsAs(listOf(VIEW))
            }
            verify { permissionService.permissionsByHanke(USERNAME) }
        }

        @Test
        fun `Should return empty when there are no permission roles`() {
            every { permissionService.permissionsByHanke(USERNAME) } returns emptyList()

            val response: Map<String, WhoamiResponse> =
                get(url).andExpect(status().isOk).andReturnBody()

            assertThat(response).isEmpty()
            verify { permissionService.permissionsByHanke(USERNAME) }
        }

        @Test
        @WithAnonymousUser
        fun `Should return 401 when unauthorized`() {
            get(url).andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class GetHankeKayttaja {
        private val kayttajaId = HankeKayttajaFactory.KAYTTAJA_ID
        private val kayttaja =
            HankeKayttajaFactory.create(
                id = kayttajaId,
                roolit = listOf(ContactType.OMISTAJA, ContactType.TOTEUTTAJA),
            )
        private val url = "/kayttajat/$kayttajaId"

        @Test
        fun `returns 400 when id is not uuid`() {
            get("/kayttajat/not-uuid").andExpect(status().isBadRequest)
        }

        @Test
        fun `returns 404 when authorization fails`() {
            every { authorizer.authorizeKayttajaId(kayttajaId, "VIEW") } throws
                HankeKayttajaNotFoundException(kayttajaId)

            get(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI4001))

            verify { authorizer.authorizeKayttajaId(kayttajaId, "VIEW") }
        }

        @Test
        fun `returns user information and writes to disclosure log`() {
            every { authorizer.authorizeKayttajaId(kayttajaId, "VIEW") } returns true
            every { hankeKayttajaService.getKayttaja(kayttajaId) } returns kayttaja

            val response: HankeKayttajaDto = get(url).andExpect(status().isOk).andReturnBody()

            assertThat(response).all {
                prop(HankeKayttajaDto::id).isEqualTo(kayttajaId)
                prop(HankeKayttajaDto::sahkoposti).isEqualTo(HankeKayttajaFactory.KAKE_EMAIL)
                prop(HankeKayttajaDto::etunimi).isEqualTo(HankeKayttajaFactory.KAKE)
                prop(HankeKayttajaDto::sukunimi).isEqualTo(HankeKayttajaFactory.KATSELIJA)
                prop(HankeKayttajaDto::puhelinnumero).isEqualTo(HankeKayttajaFactory.KAKE_PUHELIN)
                prop(HankeKayttajaDto::kayttooikeustaso).isEqualTo(Kayttooikeustaso.KATSELUOIKEUS)
                prop(HankeKayttajaDto::tunnistautunut).isEqualTo(true)
                prop(HankeKayttajaDto::roolit)
                    .containsExactly(ContactType.OMISTAJA, ContactType.TOTEUTTAJA)
                prop(HankeKayttajaDto::kutsuttu).isEqualTo(HankeKayttajaFactory.INVITATION_DATE)
            }
            verifySequence {
                authorizer.authorizeKayttajaId(kayttajaId, "VIEW")
                hankeKayttajaService.getKayttaja(kayttajaId)
                disclosureLogService.saveForHankeKayttaja(kayttaja.toDto(), USERNAME)
            }
        }
    }

    @Nested
    inner class GetHankeKayttajat {

        @Test
        fun `With valid request returns users of given hanke and logs audit`() {
            val hanke = HankeFactory.create()
            val testData =
                listOf(
                    HankeKayttajaFactory.createHankeKayttaja(
                        1, ContactType.OMISTAJA, ContactType.MUU),
                    HankeKayttajaFactory.createHankeKayttaja(2),
                    HankeKayttajaFactory.createHankeKayttaja(3),
                )
            every { authorizer.authorizeHankeTunnus(HANKE_TUNNUS, VIEW.name) } returns true
            every { hankeService.findIdentifier(HANKE_TUNNUS) } returns hanke.identifier()
            every { hankeKayttajaService.getKayttajatByHankeId(hanke.id) } returns testData

            val response: HankeKayttajaResponse =
                getHankeKayttajat().andExpect(status().isOk).andReturnBody()

            assertThat(response.kayttajat).hasSize(3)
            with(response.kayttajat.first()) {
                assertThat(id).isNotNull()
                assertThat(etunimi).isEqualTo("test1")
                assertThat(sukunimi).isEqualTo("name1")
                assertThat(puhelinnumero).isEqualTo("0405551111")
                assertThat(sahkoposti).isEqualTo("email.1.address.com")
                assertThat(tunnistautunut).isEqualTo(false)
                assertThat(kutsuttu).isEqualTo(HankeKayttajaFactory.INVITATION_DATE)
                assertThat(roolit).containsExactlyInAnyOrder(ContactType.OMISTAJA, ContactType.MUU)
            }
            assertThat(response.kayttajat).hasSameElementsAs(testData)
            verifyOrder {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, VIEW.name)
                hankeKayttajaService.getKayttajatByHankeId(hanke.id)
                disclosureLogService.saveForHankeKayttajat(response.kayttajat, USERNAME)
            }
        }

        @Test
        fun `getHankeKayttajat when no permission for hanke returns not found`() {
            every { authorizer.authorizeHankeTunnus(HANKE_TUNNUS, VIEW.name) } throws
                HankeNotFoundException(HANKE_TUNNUS)

            getHankeKayttajat().andExpect(status().isNotFound)

            verifyOrder { authorizer.authorizeHankeTunnus(HANKE_TUNNUS, VIEW.name) }
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
    inner class CreateNewUser {
        private val url = "/hankkeet/$HANKE_TUNNUS/kayttajat"
        private val email = "joku@sahkoposti.test"
        private val request = NewUserRequest("Joku", "Jokunen", email, "0508889999")

        @Test
        @WithAnonymousUser
        fun `Returns 401 when unauthorized token`() {
            post(url).andExpect(status().isUnauthorized)
        }

        @Test
        fun `Returns 400 with no request`() {
            post(url).andExpect(status().isBadRequest)
        }

        @Test
        fun `Returns 404 when hanke is not found or lacking permission to that hanke`() {
            every {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.CREATE_USER.name)
            } throws HankeNotFoundException(HANKE_TUNNUS)

            post(url, request).andExpect(status().isNotFound)

            verify {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.CREATE_USER.name)
            }
        }

        @Test
        fun `Returns 409 when duplicate user already exists`() {
            every {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.CREATE_USER.name)
            } returns true
            val hanke = HankeFactory.create()
            every { hankeService.loadHanke(HANKE_TUNNUS) } returns hanke
            every { hankeKayttajaService.createNewUser(request, hanke, USERNAME) } throws
                UserAlreadyExistsException(hanke, email)

            post(url, request)
                .andExpect(status().isConflict)
                .andExpect(hankeError(HankeError.HAI4006))

            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.CREATE_USER.name)
                hankeService.loadHanke(HANKE_TUNNUS)
                hankeKayttajaService.createNewUser(request, hanke, USERNAME)
            }
        }

        @Test
        fun `Returns information about the created user`() {
            every {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.CREATE_USER.name)
            } returns true
            val hanke = HankeFactory.create()
            every { hankeService.loadHanke(HANKE_TUNNUS) } returns hanke
            val dto = HankeKayttajaFactory.createDto()
            every { hankeKayttajaService.createNewUser(request, hanke, USERNAME) } returns dto

            val response =
                post(url, request).andExpect(status().isOk).andReturnBody<HankeKayttajaDto>()

            assertThat(response).isEqualTo(dto)
            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, PermissionCode.CREATE_USER.name)
                hankeService.loadHanke(HANKE_TUNNUS)
                hankeKayttajaService.createNewUser(request, hanke, USERNAME)
                disclosureLogService.saveForHankeKayttaja(response, USERNAME)
            }
        }
    }

    @Nested
    inner class UpdatePermissions {
        private val url = "/hankkeet/$HANKE_TUNNUS/kayttajat"
        private val hankeKayttajaId = UUID.fromString("5d67712f-ea0b-490c-957f-9b30bddb848c")
        private val hankeIdentifier = HankeIdentifierFactory.create()

        @Test
        @WithAnonymousUser
        fun `Returns 401 when unauthorized token`() {
            put(url).andExpect(status().isUnauthorized)
        }

        @Test
        fun `Returns not found when no permission for hanke`() {
            every {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, MODIFY_EDIT_PERMISSIONS.name)
            } throws HankeNotFoundException(HANKE_TUNNUS)
            val request =
                PermissionUpdate(
                    listOf(PermissionDto(hankeKayttajaId, Kayttooikeustaso.HANKEMUOKKAUS)))

            put(url, request).andExpect(status().isNotFound)

            verify { authorizer.authorizeHankeTunnus(HANKE_TUNNUS, MODIFY_EDIT_PERMISSIONS.name) }
            verify { hankeKayttajaService wasNot Called }
        }

        @Test
        fun `Returns 204 on success`() {
            every {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, MODIFY_EDIT_PERMISSIONS.name)
            } returns true
            every { hankeService.findIdentifier(HANKE_TUNNUS) } returns hankeIdentifier
            every {
                permissionService.hasPermission(
                    hankeIdentifier.id, USERNAME, PermissionCode.MODIFY_DELETE_PERMISSIONS)
            } returns false
            val updates = mapOf(hankeKayttajaId to Kayttooikeustaso.HANKEMUOKKAUS)
            justRun {
                hankeKayttajaService.updatePermissions(hankeIdentifier, updates, false, USERNAME)
            }
            val request =
                PermissionUpdate(
                    listOf(PermissionDto(hankeKayttajaId, Kayttooikeustaso.HANKEMUOKKAUS)))

            put(url, request).andExpect(status().isNoContent).andExpect(content().string(""))

            verifyCalls(updates)
        }

        @Test
        fun `Calls service with admin permission when user has them`() {
            every {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, MODIFY_EDIT_PERMISSIONS.name)
            } returns true
            every { hankeService.findIdentifier(HANKE_TUNNUS) } returns hankeIdentifier
            every {
                permissionService.hasPermission(
                    hankeIdentifier.id, USERNAME, PermissionCode.MODIFY_DELETE_PERMISSIONS)
            } returns true
            val updates = mapOf(hankeKayttajaId to Kayttooikeustaso.HANKEMUOKKAUS)
            justRun {
                hankeKayttajaService.updatePermissions(hankeIdentifier, updates, true, USERNAME)
            }
            val request =
                PermissionUpdate(
                    listOf(PermissionDto(hankeKayttajaId, Kayttooikeustaso.HANKEMUOKKAUS)))

            put(url, request).andExpect(status().isNoContent)

            verifyCalls(updates, deleteAdminPermission = true)
        }

        @Test
        fun `Returns forbidden when missing admin permissions`() {
            val updates = setupForException(MissingAdminPermissionException(USERNAME))
            val request =
                PermissionUpdate(
                    listOf(PermissionDto(hankeKayttajaId, Kayttooikeustaso.HANKEMUOKKAUS)))

            put(url, request)
                .andExpect(status().isForbidden)
                .andExpect(hankeError(HankeError.HAI0005))

            verifyCalls(updates)
        }

        @Test
        fun `Returns forbidden when changing own permissions`() {
            val updates = setupForException(ChangingOwnPermissionException(USERNAME))
            val request =
                PermissionUpdate(
                    listOf(PermissionDto(hankeKayttajaId, Kayttooikeustaso.HANKEMUOKKAUS)))

            put(url, request)
                .andExpect(status().isForbidden)
                .andExpect(hankeError(HankeError.HAI4002))

            verifyCalls(updates)
        }

        @Test
        fun `Returns internal server error if there are users without either permission or tunniste`() {
            val updates =
                setupForException(
                    UsersWithoutKayttooikeustasoException(missingIds = listOf(hankeKayttajaId)))
            val request =
                PermissionUpdate(
                    listOf(PermissionDto(hankeKayttajaId, Kayttooikeustaso.HANKEMUOKKAUS)))

            put(url, request)
                .andExpect(status().isInternalServerError)
                .andExpect(hankeError(HankeError.HAI4003))

            verifyCalls(updates)
        }

        @Test
        fun `Returns conflict if there would be no admins remaining`() {
            val updates = setupForException(NoAdminRemainingException(hankeIdentifier))
            val request =
                PermissionUpdate(
                    listOf(PermissionDto(hankeKayttajaId, Kayttooikeustaso.HANKEMUOKKAUS)))

            put(url, request)
                .andExpect(status().isConflict)
                .andExpect(hankeError(HankeError.HAI4003))

            verifyCalls(updates)
        }

        @Test
        fun `Returns bad request if there would be no admins remaining`() {
            val updates =
                setupForException(
                    HankeKayttajatNotFoundException(listOf(hankeKayttajaId), hankeIdentifier))
            val request =
                PermissionUpdate(
                    listOf(PermissionDto(hankeKayttajaId, Kayttooikeustaso.HANKEMUOKKAUS)))

            put(url, request)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI4001))

            verifyCalls(updates)
        }

        private fun verifyCalls(
            updates: Map<UUID, Kayttooikeustaso>,
            deleteAdminPermission: Boolean = false,
        ) {
            verifySequence {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, MODIFY_EDIT_PERMISSIONS.name)
                permissionService.hasPermission(
                    hankeIdentifier.id,
                    USERNAME,
                    PermissionCode.MODIFY_DELETE_PERMISSIONS,
                )
                hankeKayttajaService.updatePermissions(
                    hankeIdentifier,
                    updates,
                    deleteAdminPermission,
                    USERNAME,
                )
            }
        }

        private fun setupForException(ex: Throwable): Map<UUID, Kayttooikeustaso> {
            every {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, MODIFY_EDIT_PERMISSIONS.name)
            } returns true
            every { hankeService.findIdentifier(HANKE_TUNNUS) } returns hankeIdentifier
            every {
                permissionService.hasPermission(
                    hankeIdentifier.id, USERNAME, PermissionCode.MODIFY_DELETE_PERMISSIONS)
            } returns false
            val updates = mapOf(hankeKayttajaId to Kayttooikeustaso.HANKEMUOKKAUS)
            every {
                hankeKayttajaService.updatePermissions(hankeIdentifier, updates, false, USERNAME)
            } throws ex

            return updates
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
        fun `Returns 200 with information on success`() {
            val kayttaja = HankeKayttajaFactory.create(id = kayttajaId)
            val hanke = HankeFactory.create(id = kayttaja.hankeId)
            every {
                hankeKayttajaService.createPermissionFromToken(USERNAME, tunniste, any())
            } returns kayttaja
            every { hankeService.loadHankeById(kayttaja.hankeId) } returns hanke

            val response: TunnistautuminenResponse =
                post(url, Tunnistautuminen(tunniste)).andExpect(status().isOk).andReturnBody()

            assertThat(response).all {
                prop(TunnistautuminenResponse::kayttajaId).isEqualTo(kayttaja.id)
                prop(TunnistautuminenResponse::hankeTunnus).isEqualTo(hanke.hankeTunnus)
                prop(TunnistautuminenResponse::hankeNimi).isEqualTo(hanke.nimi)
            }
            verify {
                hankeKayttajaService.createPermissionFromToken(USERNAME, tunniste, any())
                hankeService.loadHankeById(kayttaja.hankeId)
            }
        }

        @Test
        fun `Returns 500 when cannot retrieve verified name from Profiil`() {
            every {
                hankeKayttajaService.createPermissionFromToken(USERNAME, tunniste, any())
            } throws VerifiedNameNotFound("Verified name not found from profile.")

            post(url, Tunnistautuminen(tunniste))
                .andExpect(status().isInternalServerError)
                .andExpect(hankeError(HankeError.HAI4007))

            verify {
                hankeKayttajaService.createPermissionFromToken(USERNAME, tunniste, any())
                hankeService wasNot Called
            }
        }

        @Test
        fun `Returns 404 when tunniste not found`() {
            every {
                hankeKayttajaService.createPermissionFromToken(USERNAME, tunniste, any())
            } throws TunnisteNotFoundException(USERNAME, tunniste)

            post(url, Tunnistautuminen(tunniste))
                .andExpect(status().isNotFound)
                .andExpect(hankeError(HankeError.HAI4004))

            verify { hankeKayttajaService.createPermissionFromToken(USERNAME, tunniste, any()) }
        }

        @Test
        fun `Returns 409 when user already has a permission`() {
            every {
                hankeKayttajaService.createPermissionFromToken(USERNAME, tunniste, any())
            } throws UserAlreadyHasPermissionException(USERNAME, tunnisteId, permissionId)

            post(url, Tunnistautuminen(tunniste))
                .andExpect(status().isConflict)
                .andExpect(hankeError(HankeError.HAI4003))

            verify { hankeKayttajaService.createPermissionFromToken(USERNAME, tunniste, any()) }
        }

        @Test
        fun `Returns 409 when other user already has a permission for the hanke kayttaja`() {
            every {
                hankeKayttajaService.createPermissionFromToken(USERNAME, tunniste, any())
            } throws
                PermissionAlreadyExistsException(USERNAME, "Other user", kayttajaId, permissionId)

            post(url, Tunnistautuminen(tunniste))
                .andExpect(status().isConflict)
                .andExpect(hankeError(HankeError.HAI4003))

            verify { hankeKayttajaService.createPermissionFromToken(USERNAME, tunniste, any()) }
        }
    }

    @Nested
    inner class ResendInvitations {
        private val kayttajaId = HankeKayttajaFactory.KAYTTAJA_ID
        private val url = "/kayttajat/$kayttajaId/kutsu"

        @Test
        fun `Returns 404 if current user doesn't have permission for hanke`() {
            every { authorizer.authorizeKayttajaId(kayttajaId, RESEND_INVITATION.name) } throws
                HankeKayttajaNotFoundException(kayttajaId)

            post(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI4001))

            verifySequence { authorizer.authorizeKayttajaId(kayttajaId, RESEND_INVITATION.name) }
        }

        @Test
        fun `Returns 409 if kayttaja already has permission`() {
            every { authorizer.authorizeKayttajaId(kayttajaId, RESEND_INVITATION.name) } returns
                true
            every { hankeKayttajaService.resendInvitation(kayttajaId, USERNAME) } throws
                UserAlreadyHasPermissionException(USERNAME, kayttajaId, 41)

            post(url).andExpect(status().isConflict).andExpect(hankeError(HankeError.HAI4003))

            verifySequence {
                authorizer.authorizeKayttajaId(kayttajaId, RESEND_INVITATION.name)
                hankeKayttajaService.resendInvitation(kayttajaId, USERNAME)
            }
        }

        @Test
        fun `Returns 409 if current user doesn't have a kayttaja`() {
            every { authorizer.authorizeKayttajaId(kayttajaId, RESEND_INVITATION.name) } returns
                true
            every { hankeKayttajaService.resendInvitation(kayttajaId, USERNAME) } throws
                CurrentUserWithoutKayttajaException(USERNAME)

            post(url).andExpect(status().isConflict).andExpect(hankeError(HankeError.HAI4003))

            verifySequence {
                authorizer.authorizeKayttajaId(kayttajaId, RESEND_INVITATION.name)
                hankeKayttajaService.resendInvitation(kayttajaId, USERNAME)
            }
        }

        @Test
        fun `Returns 200 if invitation resent`() {
            every { authorizer.authorizeKayttajaId(kayttajaId, RESEND_INVITATION.name) } returns
                true
            val hankekayttaja = HankeKayttajaFactory.create()
            every { hankeKayttajaService.resendInvitation(kayttajaId, USERNAME) } returns
                hankekayttaja

            val response: HankeKayttajaDto = post(url).andExpect(status().isOk).andReturnBody()

            assertThat(response).all {
                prop(HankeKayttajaDto::id).isEqualTo(hankekayttaja.id)
                prop(HankeKayttajaDto::kayttooikeustaso).isEqualTo(hankekayttaja.kayttooikeustaso)
                prop(HankeKayttajaDto::kutsuttu).isEqualTo(hankekayttaja.kutsuttu)
            }
            verifySequence {
                authorizer.authorizeKayttajaId(kayttajaId, RESEND_INVITATION.name)
                hankeKayttajaService.resendInvitation(kayttajaId, USERNAME)
                disclosureLogService.saveForHankeKayttaja(response, USERNAME)
            }
        }
    }

    @Nested
    inner class UpdateOwnContactInfo {
        private val hanketunnus = "HAI98-AAA"
        private val url = "/hankkeet/$hanketunnus/kayttajat/self"
        private val update = ContactUpdate("updated@email.test", "9991111")

        @Test
        fun `Returns 400 when email is blank`() {
            val incompleteUpdate = update.copy(sahkoposti = " ")

            put(url, incompleteUpdate)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI0003))
        }

        @Test
        fun `Returns 400 when phone number is empty`() {
            val incompleteUpdate = update.copy(puhelinnumero = "")

            put(url, incompleteUpdate)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI0003))
        }

        @Test
        fun `Returns 404 if hanke not found or user doesn't have permission for it`() {
            every { authorizer.authorizeHankeTunnus(hanketunnus, VIEW.name) } throws
                HankeNotFoundException(hanketunnus)

            put(url, update)
                .andExpect(status().isNotFound)
                .andExpect(hankeError(HankeError.HAI1001))

            verifySequence { authorizer.authorizeHankeTunnus(hanketunnus, VIEW.name) }
        }

        @Test
        fun `Returns updated info when update successful`() {
            every { authorizer.authorizeHankeTunnus(hanketunnus, VIEW.name) } returns true
            every {
                hankeKayttajaService.updateOwnContactInfo(hanketunnus, update, USERNAME)
            } returns
                HankeKayttajaFactory.create(
                    sahkoposti = update.sahkoposti,
                    puhelinnumero = update.puhelinnumero,
                )

            val response: HankeKayttajaDto =
                put(url, update).andExpect(status().isOk).andReturnBody()

            assertThat(response).all {
                prop(HankeKayttajaDto::sahkoposti).isEqualTo(update.sahkoposti)
                prop(HankeKayttajaDto::puhelinnumero).isEqualTo(update.puhelinnumero)
            }
            verifySequence {
                authorizer.authorizeHankeTunnus(hanketunnus, VIEW.name)
                hankeKayttajaService.updateOwnContactInfo(hanketunnus, update, USERNAME)
                disclosureLogService.saveForHankeKayttaja(response, USERNAME)
            }
        }
    }

    @Nested
    inner class UpdateKayttajaInfo {
        private val hanketunnus = "HAI98-AAA"
        private val userId = UUID.fromString("5d67712f-ea0b-490c-957f-9b30bddb848c")
        private val url = "/hankkeet/$hanketunnus/kayttajat/$userId"
        private val update = KayttajaUpdate("updated@email.test", "9991111")

        @Test
        fun `Returns 400 if invalid data`() {
            val update = KayttajaUpdate("updated@email.test", "")

            put(url, update)
                .andExpect(status().isBadRequest)
                .andExpect(hankeError(HankeError.HAI0003))
        }

        @Test
        fun `Returns 404 if hanke not found or user doesn't have permission for it`() {
            every { authorizer.authorizeHankeTunnus(hanketunnus, MODIFY_USER.name) } throws
                HankeNotFoundException(hanketunnus)

            put(url, update)
                .andExpect(status().isNotFound)
                .andExpect(hankeError(HankeError.HAI1001))

            verifySequence { authorizer.authorizeHankeTunnus(hanketunnus, MODIFY_USER.name) }
        }

        @Test
        fun `Returns 404 if user is not in hanke`() {
            every { authorizer.authorizeHankeTunnus(hanketunnus, MODIFY_USER.name) } returns true
            every { hankeKayttajaService.updateKayttajaInfo(hanketunnus, update, userId) } throws
                HankeKayttajaNotFoundException(userId)

            put(url, update)
                .andExpect(status().isNotFound)
                .andExpect(hankeError(HankeError.HAI4001))

            verifySequence {
                authorizer.authorizeHankeTunnus(hanketunnus, MODIFY_USER.name)
                hankeKayttajaService.updateKayttajaInfo(hanketunnus, update, userId)
            }
        }

        @Test
        fun `Returns 409 if user is identified and try to change name`() {
            val update = KayttajaUpdate("updated@email.test", "9991111", "Uusi", "Nimi")
            every { authorizer.authorizeHankeTunnus(hanketunnus, MODIFY_USER.name) } returns true
            every { hankeKayttajaService.updateKayttajaInfo(hanketunnus, update, userId) } throws
                UserAlreadyHasPermissionException(userId.toString(), userId, 1)

            put(url, update)
                .andExpect(status().isConflict)
                .andExpect(hankeError(HankeError.HAI4003))

            verifySequence {
                authorizer.authorizeHankeTunnus(hanketunnus, MODIFY_USER.name)
                hankeKayttajaService.updateKayttajaInfo(hanketunnus, update, userId)
            }
        }

        @Test
        fun `Returns updated info when all info update successful`() {
            val update = KayttajaUpdate("updated@email.test", "9991111", "Uusi", "Nimi")
            every { authorizer.authorizeHankeTunnus(hanketunnus, MODIFY_USER.name) } returns true
            every { hankeKayttajaService.updateKayttajaInfo(hanketunnus, update, userId) } returns
                HankeKayttajaFactory.create(
                    etunimi = update.etunimi!!,
                    sukunimi = update.sukunimi!!,
                    sahkoposti = update.sahkoposti,
                    puhelinnumero = update.puhelinnumero,
                )

            val response: HankeKayttajaDto =
                put(url, update).andExpect(status().isOk).andReturnBody()

            assertThat(response).all {
                prop(HankeKayttajaDto::sahkoposti).isEqualTo(update.sahkoposti)
                prop(HankeKayttajaDto::puhelinnumero).isEqualTo(update.puhelinnumero)
                prop(HankeKayttajaDto::etunimi).isEqualTo(update.etunimi)
                prop(HankeKayttajaDto::sukunimi).isEqualTo(update.sukunimi)
            }
            verifySequence {
                authorizer.authorizeHankeTunnus(hanketunnus, MODIFY_USER.name)
                hankeKayttajaService.updateKayttajaInfo(hanketunnus, update, userId)
                disclosureLogService.saveForHankeKayttaja(response, USERNAME)
            }
        }

        @Test
        fun `Returns updated info when contact info update successful`() {
            every { authorizer.authorizeHankeTunnus(hanketunnus, MODIFY_USER.name) } returns true
            every { hankeKayttajaService.updateKayttajaInfo(hanketunnus, update, userId) } returns
                HankeKayttajaFactory.create(
                    sahkoposti = update.sahkoposti,
                    puhelinnumero = update.puhelinnumero,
                )

            val response: HankeKayttajaDto =
                put(url, update).andExpect(status().isOk).andReturnBody()

            assertThat(response).all {
                prop(HankeKayttajaDto::sahkoposti).isEqualTo(update.sahkoposti)
                prop(HankeKayttajaDto::puhelinnumero).isEqualTo(update.puhelinnumero)
            }
            verifySequence {
                authorizer.authorizeHankeTunnus(hanketunnus, MODIFY_USER.name)
                hankeKayttajaService.updateKayttajaInfo(hanketunnus, update, userId)
                disclosureLogService.saveForHankeKayttaja(response, USERNAME)
            }
        }
    }

    @Nested
    inner class CheckForDelete {
        private val kayttajaId = UUID.fromString("294af703-7982-47f8-bc08-f08ace485a2b")
        private val url = "/kayttajat/$kayttajaId/deleteInfo"

        @Test
        fun `Returns not found when the call is not authorized`() {
            every { authorizer.authorizeKayttajaId(kayttajaId, DELETE_USER.name) } throws
                HankeKayttajaNotFoundException(kayttajaId)

            get(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI4001))

            verifySequence { authorizer.authorizeKayttajaId(kayttajaId, DELETE_USER.name) }
        }

        @Test
        fun `Returns not found when hankekayttaja is not found`() {
            every { authorizer.authorizeKayttajaId(kayttajaId, DELETE_USER.name) } returns true
            every { deleteService.checkForDelete(kayttajaId) } throws
                HankeKayttajaNotFoundException(kayttajaId)

            get(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI4001))

            verifySequence {
                authorizer.authorizeKayttajaId(kayttajaId, DELETE_USER.name)
                deleteService.checkForDelete(kayttajaId)
            }
        }

        @Test
        fun `Returns delete info`() {
            every { authorizer.authorizeKayttajaId(kayttajaId, DELETE_USER.name) } returns true
            val draftInfo = DeleteInfo.HakemusDetails("Draft", null, null)
            val pendingInfo =
                DeleteInfo.HakemusDetails("Pending", "JS230001", ApplicationStatus.PENDING)
            val decisionInfo =
                DeleteInfo.HakemusDetails("Decision", "JS230002", ApplicationStatus.DECISION)
            every { deleteService.checkForDelete(kayttajaId) } returns
                DeleteInfo(listOf(pendingInfo, decisionInfo), listOf(draftInfo), true)

            val response: DeleteInfo = get(url).andExpect(status().isOk).andReturnBody()

            assertThat(response).all {
                prop(DeleteInfo::onlyOmistajanYhteyshenkilo).isTrue()
                prop(DeleteInfo::activeHakemukset).containsExactly(pendingInfo, decisionInfo)
                prop(DeleteInfo::draftHakemukset).containsExactly(draftInfo)
            }
            verifySequence {
                authorizer.authorizeKayttajaId(kayttajaId, DELETE_USER.name)
                deleteService.checkForDelete(kayttajaId)
            }
        }
    }

    @Nested
    inner class Delete {
        private val kayttajaId = UUID.fromString("294af703-7982-47f8-bc08-f08ace485a2b")
        private val url = "/kayttajat/$kayttajaId"

        @Test
        fun `Returns not found when the call is not authorized`() {
            every { authorizer.authorizeKayttajaId(kayttajaId, DELETE_USER.name) } throws
                HankeKayttajaNotFoundException(kayttajaId)

            delete(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI4001))

            verifySequence { authorizer.authorizeKayttajaId(kayttajaId, DELETE_USER.name) }
        }

        @Test
        fun `Returns not found when hankekayttaja is not found`() {
            every { authorizer.authorizeKayttajaId(kayttajaId, DELETE_USER.name) } returns true
            every { deleteService.delete(kayttajaId, USERNAME) } throws
                HankeKayttajaNotFoundException(kayttajaId)

            delete(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI4001))

            verifySequence {
                authorizer.authorizeKayttajaId(kayttajaId, DELETE_USER.name)
                deleteService.delete(kayttajaId, USERNAME)
            }
        }

        @Test
        fun `Returns 409 when hankekayttaja is the only one with Kaikki Oikeudet`() {
            every { authorizer.authorizeKayttajaId(kayttajaId, DELETE_USER.name) } returns true
            every { deleteService.delete(kayttajaId, USERNAME) } throws
                NoAdminRemainingException(TestHankeIdentifier(132, "JS44-11"))

            delete(url).andExpect(status().isConflict).andExpect(hankeError(HankeError.HAI4003))

            verifySequence {
                authorizer.authorizeKayttajaId(kayttajaId, DELETE_USER.name)
                deleteService.delete(kayttajaId, USERNAME)
            }
        }

        @Test
        fun `Returns 409 when hankekayttaja is the only contact for an omistaja`() {
            every { authorizer.authorizeKayttajaId(kayttajaId, DELETE_USER.name) } returns true
            every { deleteService.delete(kayttajaId, USERNAME) } throws
                OnlyOmistajaContactException(kayttajaId, listOf(13, 15))

            delete(url).andExpect(status().isConflict).andExpect(hankeError(HankeError.HAI4003))

            verifySequence {
                authorizer.authorizeKayttajaId(kayttajaId, DELETE_USER.name)
                deleteService.delete(kayttajaId, USERNAME)
            }
        }

        @Test
        fun `Returns 409 when hankekayttaja is an yhteyshenkilo on an active hakemus`() {
            every { authorizer.authorizeKayttajaId(kayttajaId, DELETE_USER.name) } returns true
            every { deleteService.delete(kayttajaId, USERNAME) } throws
                HasActiveApplicationsException(kayttajaId, listOf(13, 15))

            delete(url).andExpect(status().isConflict).andExpect(hankeError(HankeError.HAI4003))

            verifySequence {
                authorizer.authorizeKayttajaId(kayttajaId, DELETE_USER.name)
                deleteService.delete(kayttajaId, USERNAME)
            }
        }

        @Test
        fun `Returns 204 when hankekayttaja was deleted`() {
            every { authorizer.authorizeKayttajaId(kayttajaId, DELETE_USER.name) } returns true
            justRun { deleteService.delete(kayttajaId, USERNAME) }

            delete(url).andExpect(status().isNoContent).andExpect(content().string(""))

            verifySequence {
                authorizer.authorizeKayttajaId(kayttajaId, DELETE_USER.name)
                deleteService.delete(kayttajaId, USERNAME)
            }
        }
    }
}
