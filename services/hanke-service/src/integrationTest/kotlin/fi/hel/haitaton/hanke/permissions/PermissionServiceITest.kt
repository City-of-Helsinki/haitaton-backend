package fi.hel.haitaton.hanke.permissions

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import fi.hel.haitaton.hanke.DatabaseTest
import fi.hel.haitaton.hanke.HankeService
import fi.hel.haitaton.hanke.factory.HankeFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
@WithMockUser(username = "test7358")
class PermissionServiceITest : DatabaseTest() {

    val username = "user"

    @Autowired lateinit var permissionService: PermissionService
    @Autowired lateinit var permissionRepository: PermissionRepository
    @Autowired lateinit var roleRepository: RoleRepository
    @Autowired lateinit var hankeService: HankeService

    companion object {
        @JvmStatic
        fun roleArguments() =
            listOf(
                Arguments.of(Role.KAIKKI_OIKEUDET, PermissionCode.values()),
                Arguments.of(
                    Role.KAIKKIEN_MUOKKAUS,
                    arrayOf(
                        PermissionCode.VIEW,
                        PermissionCode.MODIFY_VIEW_PERMISSIONS,
                        PermissionCode.EDIT,
                        PermissionCode.MODIFY_EDIT_PERMISSIONS,
                        PermissionCode.EDIT_APPLICATIONS,
                        PermissionCode.MODIFY_APPLICATION_PERMISSIONS,
                    )
                ),
                Arguments.of(
                    Role.HANKEMUOKKAUS,
                    arrayOf(
                        PermissionCode.VIEW,
                        PermissionCode.EDIT,
                    )
                ),
                Arguments.of(
                    Role.HAKEMUSASIOINTI,
                    arrayOf(
                        PermissionCode.VIEW,
                        PermissionCode.EDIT_APPLICATIONS,
                    )
                ),
                Arguments.of(Role.KATSELUOIKEUS, arrayOf(PermissionCode.VIEW)),
            )
    }

    @ParameterizedTest
    @MethodSource("roleArguments")
    fun `roles have correct permissions`(role: Role, allowedPermissions: Array<PermissionCode>) {
        val roleEntity = roleRepository.findOneByRole(role)

        allowedPermissions.forEach { code ->
            assertThat(code).transform { PermissionService.hasPermission(roleEntity, it) }.isTrue()
        }
        PermissionCode.values()
            .filter { !allowedPermissions.contains(it) }
            .forEach { code ->
                assertThat(code)
                    .transform { PermissionService.hasPermission(roleEntity, it) }
                    .isFalse()
            }
    }

    @Test
    fun `getAllowedHankeIds without permissions returns empty list`() {
        val response = permissionService.getAllowedHankeIds(username, PermissionCode.EDIT)

        assertEquals(listOf<Int>(), response)
    }

    @Test
    fun `getAllowedHankeIds with permissions returns list of IDs`() {
        val kaikkiOikeudet = roleRepository.findOneByRole(Role.KAIKKI_OIKEUDET)
        val hankkeet = saveSeveralHanke(3)
        hankkeet
            .map { it.id!! }
            .forEach {
                permissionRepository.save(
                    PermissionEntity(
                        userId = username,
                        hankeId = it,
                        role = kaikkiOikeudet,
                    )
                )
            }

        val response = permissionService.getAllowedHankeIds(username, PermissionCode.EDIT)

        assertEquals(hankkeet.map { it.id }, response)
    }

    @Test
    fun `getAllowedHankeIds return ids with correct permissions`() {
        val kaikkiOikeudet = roleRepository.findOneByRole(Role.KAIKKI_OIKEUDET)
        val hankemuokkaus = roleRepository.findOneByRole(Role.HANKEMUOKKAUS)
        val hakemusasiointi = roleRepository.findOneByRole(Role.HAKEMUSASIOINTI)
        val katseluoikeus = roleRepository.findOneByRole(Role.KATSELUOIKEUS)
        val hankkeet = saveSeveralHanke(4)
        listOf(kaikkiOikeudet, hankemuokkaus, hakemusasiointi, katseluoikeus).zip(hankkeet) {
            role,
            hanke ->
            permissionRepository.save(
                PermissionEntity(
                    userId = username,
                    hankeId = hanke.id!!,
                    role = role,
                )
            )
        }

        val response = permissionService.getAllowedHankeIds(username, PermissionCode.EDIT)

        assertEquals(listOf(hankkeet[0].id, hankkeet[1].id), response)
    }

    @Test
    fun `hasPermission returns false without permissions`() {
        assertFalse(permissionService.hasPermission(2, username, PermissionCode.EDIT))
    }

    @Test
    fun `hasPermission with correct permission`() {
        val kaikkiOikeudet = roleRepository.findOneByRole(Role.KAIKKI_OIKEUDET)
        val hankeId = saveSeveralHanke(1)[0].id!!
        permissionRepository.save(
            PermissionEntity(userId = username, hankeId = hankeId, role = kaikkiOikeudet)
        )

        assertTrue(permissionService.hasPermission(hankeId, username, PermissionCode.EDIT))
    }

    @Test
    fun `hasPermission with insufficient permissions`() {
        val hakemusasiointi = roleRepository.findOneByRole(Role.HAKEMUSASIOINTI)
        val hankeId = saveSeveralHanke(1)[0].id!!
        permissionRepository.save(
            PermissionEntity(userId = username, hankeId = hankeId, role = hakemusasiointi)
        )

        assertFalse(permissionService.hasPermission(hankeId, username, PermissionCode.EDIT))
    }

    @Test
    fun `setPermission creates a new permission`() {
        val hankeId = saveSeveralHanke(1)[0].id!!

        permissionService.setPermission(hankeId, username, Role.KATSELUOIKEUS)

        val permissions = permissionRepository.findAll()
        assertThat(permissions).hasSize(1)
        assertEquals(hankeId, permissions[0].hankeId)
        assertEquals(Role.KATSELUOIKEUS, permissions[0].role.role)
    }

    @Test
    fun `setPermission updates an existing permission`() {
        val hankeId = saveSeveralHanke(1)[0].id!!
        val role = roleRepository.findOneByRole(Role.KATSELUOIKEUS)
        permissionRepository.save(
            PermissionEntity(userId = username, hankeId = hankeId, role = role)
        )

        permissionService.setPermission(hankeId, username, Role.HAKEMUSASIOINTI)

        val permissions = permissionRepository.findAll()
        assertThat(permissions).hasSize(1)
        assertEquals(hankeId, permissions[0].hankeId)
        assertEquals(Role.HAKEMUSASIOINTI, permissions[0].role.role)
    }

    private fun saveSeveralHanke(n: Int) =
        createSeveralHanke(n).map { hankeService.createHanke(it) }

    private fun createSeveralHanke(n: Int) = (1..n).map { HankeFactory.create(id = null) }
}
