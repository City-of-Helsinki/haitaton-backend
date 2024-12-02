package fi.hel.haitaton.hanke.permissions

import fi.hel.haitaton.hanke.logging.PermissionLoggingService
import io.mockk.checkUnnecessaryStub
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifySequence
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class PermissionServiceTest {

    private val permissionRepository: PermissionRepository = mockk()
    private val kayttooikeustasoRepository: KayttooikeustasoRepository = mockk()
    private val logService: PermissionLoggingService = mockk()

    private val permissionService =
        PermissionService(permissionRepository, kayttooikeustasoRepository, logService)

    @BeforeEach
    fun cleanup() {
        clearAllMocks()
    }

    @AfterEach
    fun checkMocks() {
        checkUnnecessaryStub()
        confirmVerified(permissionRepository, kayttooikeustasoRepository, logService)
    }

    @Nested
    inner class HasPermissionWithKayttooikeustaso {
        @Test
        fun `calls repository only once for one kayttooikeustaso`() {
            every {
                kayttooikeustasoRepository.findOneByKayttooikeustaso(Kayttooikeustaso.HANKEMUOKKAUS)
            } returns
                KayttooikeustasoEntity(
                    kayttooikeustaso = Kayttooikeustaso.HANKEMUOKKAUS,
                    permissionCode = PermissionCode.EDIT.code,
                )

            permissionService.hasPermission(Kayttooikeustaso.HANKEMUOKKAUS, PermissionCode.EDIT)
            permissionService.hasPermission(Kayttooikeustaso.HANKEMUOKKAUS, PermissionCode.VIEW)
            permissionService.hasPermission(Kayttooikeustaso.HANKEMUOKKAUS, PermissionCode.DELETE)

            verifySequence {
                kayttooikeustasoRepository.findOneByKayttooikeustaso(Kayttooikeustaso.HANKEMUOKKAUS)
            }
        }
    }
}
