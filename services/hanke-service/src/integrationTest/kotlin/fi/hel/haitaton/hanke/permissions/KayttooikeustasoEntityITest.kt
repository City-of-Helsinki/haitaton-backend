package fi.hel.haitaton.hanke.permissions

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.key
import fi.hel.haitaton.hanke.IntegrationTest
import fi.hel.haitaton.hanke.hasSameElementsAs
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired

class KayttooikeustasoEntityITest : IntegrationTest() {

    @Autowired lateinit var kayttooikeustasoRepository: KayttooikeustasoRepository

    companion object {
        @JvmStatic
        fun kayttooikeustasot() =
            listOf(
                Arguments.of(Kayttooikeustaso.KAIKKI_OIKEUDET, PermissionCode.entries),
                Arguments.of(
                    Kayttooikeustaso.KAIKKIEN_MUOKKAUS,
                    listOf(
                        PermissionCode.VIEW,
                        PermissionCode.MODIFY_VIEW_PERMISSIONS,
                        PermissionCode.EDIT,
                        PermissionCode.MODIFY_EDIT_PERMISSIONS,
                        PermissionCode.EDIT_APPLICATIONS,
                        PermissionCode.MODIFY_APPLICATION_PERMISSIONS,
                        PermissionCode.RESEND_INVITATION,
                        PermissionCode.CREATE_USER,
                        PermissionCode.MODIFY_USER,
                        PermissionCode.DELETE_USER,
                    )
                ),
                Arguments.of(
                    Kayttooikeustaso.HANKEMUOKKAUS,
                    listOf(
                        PermissionCode.VIEW,
                        PermissionCode.EDIT,
                        PermissionCode.RESEND_INVITATION,
                        PermissionCode.CREATE_USER,
                        PermissionCode.MODIFY_USER,
                    )
                ),
                Arguments.of(
                    Kayttooikeustaso.HAKEMUSASIOINTI,
                    listOf(
                        PermissionCode.VIEW,
                        PermissionCode.EDIT_APPLICATIONS,
                        PermissionCode.RESEND_INVITATION,
                        PermissionCode.CREATE_USER,
                        PermissionCode.MODIFY_USER,
                    )
                ),
                Arguments.of(Kayttooikeustaso.KATSELUOIKEUS, listOf(PermissionCode.VIEW)),
            )
    }

    @ParameterizedTest
    @MethodSource("kayttooikeustasot")
    fun `All kayttooikeustaso have correct permissions`(
        kayttooikeustaso: Kayttooikeustaso,
        allowedPermissions: List<PermissionCode>
    ) {
        val kayttooikeustasoEntity =
            kayttooikeustasoRepository.findOneByKayttooikeustaso(kayttooikeustaso)

        val permissions =
            PermissionCode.entries.associateWith { kayttooikeustasoEntity.hasPermission(it) }

        PermissionCode.entries.forEach {
            assertThat(permissions).key(it).isEqualTo(allowedPermissions.contains(it))
        }
    }

    @ParameterizedTest
    @MethodSource("kayttooikeustasot")
    fun `Kayttooikeustaso returns correct permission codes`(
        kayttooikeustaso: Kayttooikeustaso,
        allowedPermissions: List<PermissionCode>
    ) {
        val kayttooikeustasoEntity =
            kayttooikeustasoRepository.findOneByKayttooikeustaso(kayttooikeustaso)

        val permissions = kayttooikeustasoEntity.permissionCodes

        assertThat(permissions).hasSameElementsAs(allowedPermissions.toList())
    }
}
