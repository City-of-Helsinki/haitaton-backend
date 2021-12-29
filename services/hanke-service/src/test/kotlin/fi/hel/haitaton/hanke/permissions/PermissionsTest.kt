package fi.hel.haitaton.hanke.permissions

import assertk.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class PermissionsTest {

    inline fun <reified T: Any> mock() = Mockito.mock(T::class.java)

    @Test
    fun combinePermissionCodesAndPermissionCodeToCodesShouldRoundTrip() {
        val repo: PermissionRepository = mock()
        val service = PermissionService(repo)
        val expectedPermissions = listOf(PermissionCode.VIEW, PermissionCode.EDIT, PermissionCode.MODIFY_EDIT_PERMISSIONS)
        val code = service.combinePermissionCodes(expectedPermissions)
        val actualPermissions = service.permissionCodeToCodes(code)
        assertThat { expectedPermissions.containsAll(actualPermissions) }
        assertThat { actualPermissions.containsAll(expectedPermissions) }
    }

}