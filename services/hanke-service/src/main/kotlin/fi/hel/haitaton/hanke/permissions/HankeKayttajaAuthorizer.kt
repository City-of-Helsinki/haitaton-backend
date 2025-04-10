package fi.hel.haitaton.hanke.permissions

import fi.hel.haitaton.hanke.HankeRepository
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component

@Component
class HankeKayttajaAuthorizer(
    permissionService: PermissionService,
    hankeRepository: HankeRepository,
    private val hankekayttajaRepository: HankekayttajaRepository,
) : Authorizer(permissionService, hankeRepository) {

    private fun authorizeKayttajaId(
        hankeKayttajaId: UUID,
        permissionCode: PermissionCode,
        editDeniedForCompleted: Boolean,
    ): Boolean {
        val hankeId = hankekayttajaRepository.findByIdOrNull(hankeKayttajaId)?.hankeId
        authorize(hankeId, permissionCode, editDeniedForCompleted) {
            HankeKayttajaNotFoundException(hankeKayttajaId)
        }
        return true
    }

    fun authorizeKayttajaId(hankeKayttajaId: UUID, permissionCode: String): Boolean =
        authorizeKayttajaId(
            hankeKayttajaId,
            PermissionCode.valueOf(permissionCode),
            editDeniedForCompleted = true,
        )

    fun authorizeResend(hankeKayttajaId: UUID, permissionCode: String) =
        authorizeKayttajaId(
            hankeKayttajaId,
            PermissionCode.valueOf(permissionCode),
            editDeniedForCompleted = false,
        )
}
