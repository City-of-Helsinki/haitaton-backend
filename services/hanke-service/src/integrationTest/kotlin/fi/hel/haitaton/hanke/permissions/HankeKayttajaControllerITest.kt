package fi.hel.haitaton.hanke.permissions

import assertk.all
import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.prop
import fi.hel.haitaton.hanke.ContactType
import fi.hel.haitaton.hanke.ControllerTest
import fi.hel.haitaton.hanke.HankeError
import fi.hel.haitaton.hanke.HankeNotFoundException
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.IntegrationTestConfiguration
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
import fi.hel.haitaton.hanke.permissions.PermissionCode.EDIT
import fi.hel.haitaton.hanke.permissions.PermissionCode.MODIFY_EDIT_PERMISSIONS
import fi.hel.haitaton.hanke.permissions.PermissionCode.RESEND_INVITATION
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
    @Autowired private lateinit var authorizer: HankeKayttajaAuthorizer

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
                kayttooikeustasoEntity = KayttooikeustasoEntity(0, kayttooikeustaso, 1 or 4)
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
                )
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
        private val kayttaja = HankeKayttajaFactory.createDto(id = kayttajaId)
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
                prop(HankeKayttajaDto::sahkoposti).isEqualTo("email.1.address.com")
                prop(HankeKayttajaDto::etunimi).isEqualTo("test1")
                prop(HankeKayttajaDto::sukunimi).isEqualTo("name1")
                prop(HankeKayttajaDto::nimi).isEqualTo("test1 name1")
                prop(HankeKayttajaDto::puhelinnumero).isEqualTo("0405551111")
                prop(HankeKayttajaDto::kayttooikeustaso).isEqualTo(Kayttooikeustaso.KATSELUOIKEUS)
                prop(HankeKayttajaDto::tunnistautunut).isEqualTo(false)
            }
            verifySequence {
                authorizer.authorizeKayttajaId(kayttajaId, "VIEW")
                hankeKayttajaService.getKayttaja(kayttajaId)
                disclosureLogService.saveDisclosureLogsForHankeKayttaja(kayttaja, USERNAME)
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
                        1,
                        ContactType.OMISTAJA,
                        ContactType.MUU
                    ),
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
                assertThat(nimi).isEqualTo("test1 name1")
                assertThat(puhelinnumero).isEqualTo("0405551111")
                assertThat(sahkoposti).isEqualTo("email.1.address.com")
                assertThat(tunnistautunut).isEqualTo(false)
                assertThat(roolit).containsExactlyInAnyOrder(ContactType.OMISTAJA, ContactType.MUU)
            }
            assertThat(response.kayttajat).hasSameElementsAs(testData)
            verifyOrder {
                authorizer.authorizeHankeTunnus(HANKE_TUNNUS, VIEW.name)
                hankeKayttajaService.getKayttajatByHankeId(hanke.id)
                disclosureLogService.saveDisclosureLogsForHankeKayttajat(
                    response.kayttajat,
                    USERNAME
                )
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

            put(
                    url,
                    PermissionUpdate(
                        listOf(PermissionDto(hankeKayttajaId, Kayttooikeustaso.HANKEMUOKKAUS))
                    )
                )
                .andExpect(status().isNotFound)

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
                    hankeIdentifier.id,
                    USERNAME,
                    PermissionCode.MODIFY_DELETE_PERMISSIONS
                )
            } returns false
            val updates = mapOf(hankeKayttajaId to Kayttooikeustaso.HANKEMUOKKAUS)
            justRun {
                hankeKayttajaService.updatePermissions(hankeIdentifier, updates, false, USERNAME)
            }

            put(
                    url,
                    PermissionUpdate(
                        listOf(PermissionDto(hankeKayttajaId, Kayttooikeustaso.HANKEMUOKKAUS))
                    )
                )
                .andExpect(status().isNoContent)
                .andExpect(content().string(""))

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
                    hankeIdentifier.id,
                    USERNAME,
                    PermissionCode.MODIFY_DELETE_PERMISSIONS
                )
            } returns true
            val updates = mapOf(hankeKayttajaId to Kayttooikeustaso.HANKEMUOKKAUS)
            justRun {
                hankeKayttajaService.updatePermissions(hankeIdentifier, updates, true, USERNAME)
            }

            put(
                    url,
                    PermissionUpdate(
                        listOf(PermissionDto(hankeKayttajaId, Kayttooikeustaso.HANKEMUOKKAUS))
                    )
                )
                .andExpect(status().isNoContent)

            verifyCalls(updates, deleteAdminPermission = true)
        }

        @Test
        fun `Returns forbidden when missing admin permissions`() {
            val updates = setupForException(MissingAdminPermissionException(USERNAME))

            put(
                    url,
                    PermissionUpdate(
                        listOf(PermissionDto(hankeKayttajaId, Kayttooikeustaso.HANKEMUOKKAUS))
                    )
                )
                .andExpect(status().isForbidden)
                .andExpect(hankeError(HankeError.HAI0005))

            verifyCalls(updates)
        }

        @Test
        fun `Returns forbidden when changing own permissions`() {
            val updates = setupForException(ChangingOwnPermissionException(USERNAME))

            put(
                    url,
                    PermissionUpdate(
                        listOf(PermissionDto(hankeKayttajaId, Kayttooikeustaso.HANKEMUOKKAUS))
                    )
                )
                .andExpect(status().isForbidden)
                .andExpect(hankeError(HankeError.HAI4002))

            verifyCalls(updates)
        }

        @Test
        fun `Returns internal server error if there are users without either permission or tunniste`() {
            val updates =
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

            verifyCalls(updates)
        }

        @Test
        fun `Returns conflict if there would be no admins remaining`() {
            val updates = setupForException(NoAdminRemainingException(hankeIdentifier))

            put(
                    url,
                    PermissionUpdate(
                        listOf(PermissionDto(hankeKayttajaId, Kayttooikeustaso.HANKEMUOKKAUS))
                    )
                )
                .andExpect(status().isConflict)
                .andExpect(hankeError(HankeError.HAI4003))

            verifyCalls(updates)
        }

        @Test
        fun `Returns bad request if there would be no admins remaining`() {
            val updates =
                setupForException(
                    HankeKayttajatNotFoundException(listOf(hankeKayttajaId), hankeIdentifier)
                )

            put(
                    url,
                    PermissionUpdate(
                        listOf(PermissionDto(hankeKayttajaId, Kayttooikeustaso.HANKEMUOKKAUS))
                    )
                )
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
                    PermissionCode.MODIFY_DELETE_PERMISSIONS
                )
                hankeKayttajaService.updatePermissions(
                    hankeIdentifier,
                    updates,
                    deleteAdminPermission,
                    USERNAME
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
                    hankeIdentifier.id,
                    USERNAME,
                    PermissionCode.MODIFY_DELETE_PERMISSIONS
                )
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
            every { hankeKayttajaService.createPermissionFromToken(USERNAME, tunniste) } returns
                kayttaja
            every { hankeService.loadHankeById(kayttaja.hankeId) } returns hanke

            val response: TunnistautuminenResponse =
                post(url, Tunnistautuminen(tunniste)).andExpect(status().isOk).andReturnBody()

            assertThat(response).all {
                prop(TunnistautuminenResponse::kayttajaId).isEqualTo(kayttaja.id)
                prop(TunnistautuminenResponse::hankeTunnus).isEqualTo(hanke.hankeTunnus)
                prop(TunnistautuminenResponse::hankeNimi).isEqualTo(hanke.nimi)
            }
            verify {
                hankeKayttajaService.createPermissionFromToken(USERNAME, tunniste)
                hankeService.loadHankeById(kayttaja.hankeId)
            }
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
        fun `Returns 204 if invitation resent`() {
            every { authorizer.authorizeKayttajaId(kayttajaId, RESEND_INVITATION.name) } returns
                true
            justRun { hankeKayttajaService.resendInvitation(kayttajaId, USERNAME) }

            post(url).andExpect(status().isNoContent).andExpect(content().string(""))

            verifySequence {
                authorizer.authorizeKayttajaId(kayttajaId, RESEND_INVITATION.name)
                hankeKayttajaService.resendInvitation(kayttajaId, USERNAME)
            }
        }
    }

    @Nested
    inner class UpdateOwnContactInfo {
        private val hanketunnus = "HAI98-AAA"
        private val url = "/hankkeet/$hanketunnus/kayttajat/self"
        private val update = ContactUpdate("updated@email.test", "9991111")

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
                    puhelinnumero = update.puhelinnumero
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
            }
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
    private val hankeKayttajaId = UUID.fromString("5d67712f-ea0b-490c-957f-9b30bddb848c")

    @Nested
    inner class UpdatePermissions {
        private val url = "/hankkeet/$HANKE_TUNNUS/kayttajat"

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

    @Nested
    inner class ResendInvitations {
        private val kayttajaId = UUID.fromString("0b384506-3ad2-4588-b032-b825c6f89bd5")
        private val url = "/kayttajat/$kayttajaId/kutsu"

        @Test
        fun `Returns not found when feature disabled`() {
            post(url).andExpect(status().isNotFound).andExpect(hankeError(HankeError.HAI0004))
        }
    }
}
