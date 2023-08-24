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
@ActiveProfiles("test")
@WithMockUser(username = "test7358")
class PermissionServiceITest : DatabaseTest() {

    val username = "user"

    @Autowired lateinit var permissionService: PermissionService
    @Autowired lateinit var permissionRepository: PermissionRepository
    @Autowired lateinit var hankeService: HankeService

    companion object {
        @JvmStatic
        fun kayttooikeustasot() =
            listOf(
                Arguments.of(Kayttooikeustaso.KAIKKI_OIKEUDET, PermissionCode.values()),
                Arguments.of(
                    Kayttooikeustaso.KAIKKIEN_MUOKKAUS,
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
                    Kayttooikeustaso.HANKEMUOKKAUS,
                    arrayOf(
                        PermissionCode.VIEW,
                        PermissionCode.EDIT,
                    )
                ),
                Arguments.of(
                    Kayttooikeustaso.HAKEMUSASIOINTI,
                    arrayOf(
                        PermissionCode.VIEW,
                        PermissionCode.EDIT_APPLICATIONS,
                    )
                ),
                Arguments.of(Kayttooikeustaso.KATSELUOIKEUS, arrayOf(PermissionCode.VIEW)),
            )
    }

    @ParameterizedTest
    @MethodSource("kayttooikeustasot")
    fun `All kayttooikeustaso have correct permissions`(
        kayttooikeustaso: Kayttooikeustaso,
        allowedPermissions: Array<PermissionCode>
    ) {
        val kayttooikeustasoEntity = permissionService.findKayttooikeustaso(kayttooikeustaso)

        allowedPermissions.forEach { code ->
            assertThat(code)
                .transform { PermissionService.hasPermission(kayttooikeustasoEntity, it) }
                .isTrue()
        }
        PermissionCode.values()
            .filter { !allowedPermissions.contains(it) }
            .forEach { code ->
                assertThat(code)
                    .transform { PermissionService.hasPermission(kayttooikeustasoEntity, it) }
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
        val kaikkiOikeudet =
            permissionService.findKayttooikeustaso(Kayttooikeustaso.KAIKKI_OIKEUDET)
        val hankkeet = saveSeveralHanke(3)
        hankkeet
            .map { it.id!! }
            .forEach {
                permissionRepository.save(
                    PermissionEntity(
                        userId = username,
                        hankeId = it,
                        kayttooikeustaso = kaikkiOikeudet,
                    )
                )
            }

        val response = permissionService.getAllowedHankeIds(username, PermissionCode.EDIT)

        assertEquals(hankkeet.map { it.id }, response)
    }

    @Test
    fun `getAllowedHankeIds return ids with correct permissions`() {
        val kaikkiOikeudet =
            permissionService.findKayttooikeustaso(Kayttooikeustaso.KAIKKI_OIKEUDET)
        val hankemuokkaus = permissionService.findKayttooikeustaso(Kayttooikeustaso.HANKEMUOKKAUS)
        val hakemusasiointi =
            permissionService.findKayttooikeustaso(Kayttooikeustaso.HAKEMUSASIOINTI)
        val katseluoikeus = permissionService.findKayttooikeustaso(Kayttooikeustaso.KATSELUOIKEUS)
        val hankkeet = saveSeveralHanke(4)
        listOf(kaikkiOikeudet, hankemuokkaus, hakemusasiointi, katseluoikeus).zip(hankkeet) {
            kayttooikeustaso,
            hanke ->
            permissionRepository.save(
                PermissionEntity(
                    userId = username,
                    hankeId = hanke.id!!,
                    kayttooikeustaso = kayttooikeustaso,
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
        val kaikkiOikeudet =
            permissionService.findKayttooikeustaso(Kayttooikeustaso.KAIKKI_OIKEUDET)
        val hankeId = saveSeveralHanke(1)[0].id!!
        permissionRepository.save(
            PermissionEntity(
                userId = username,
                hankeId = hankeId,
                kayttooikeustaso = kaikkiOikeudet
            )
        )

        assertTrue(permissionService.hasPermission(hankeId, username, PermissionCode.EDIT))
    }

    @Test
    fun `hasPermission with insufficient permissions`() {
        val hakemusasiointi =
            permissionService.findKayttooikeustaso(Kayttooikeustaso.HAKEMUSASIOINTI)
        val hankeId = saveSeveralHanke(1)[0].id!!
        permissionRepository.save(
            PermissionEntity(
                userId = username,
                hankeId = hankeId,
                kayttooikeustaso = hakemusasiointi
            )
        )

        assertFalse(permissionService.hasPermission(hankeId, username, PermissionCode.EDIT))
    }

    @Test
    fun `setPermission creates a new permission`() {
        val hankeId = saveSeveralHanke(1)[0].id!!
        permissionRepository.deleteAll() // remove permission created in hanke creation

        permissionService.setPermission(hankeId, username, Kayttooikeustaso.KATSELUOIKEUS)

        val permissions = permissionRepository.findAll()
        assertThat(permissions).hasSize(1)
        assertEquals(hankeId, permissions[0].hankeId)
        assertEquals(
            Kayttooikeustaso.KATSELUOIKEUS,
            permissions[0].kayttooikeustaso.kayttooikeustaso
        )
    }

    @Test
    fun `setPermission updates an existing permission`() {
        val hankeId = saveSeveralHanke(1)[0].id!!
        permissionRepository.deleteAll() // remove permission created in hanke creation
        val kayttooikeustaso =
            permissionService.findKayttooikeustaso(Kayttooikeustaso.KATSELUOIKEUS)
        permissionRepository.save(
            PermissionEntity(
                userId = username,
                hankeId = hankeId,
                kayttooikeustaso = kayttooikeustaso
            )
        )

        permissionService.setPermission(hankeId, username, Kayttooikeustaso.HAKEMUSASIOINTI)

        val permissions = permissionRepository.findAll()
        assertThat(permissions).hasSize(1)
        assertEquals(hankeId, permissions[0].hankeId)
        assertEquals(
            Kayttooikeustaso.HAKEMUSASIOINTI,
            permissions[0].kayttooikeustaso.kayttooikeustaso
        )
    }

    private fun saveSeveralHanke(n: Int) =
        createSeveralHanke(n).map { hankeService.createHanke(it) }

    private fun createSeveralHanke(n: Int) = (1..n).map { HankeFactory.create(id = null) }
}
