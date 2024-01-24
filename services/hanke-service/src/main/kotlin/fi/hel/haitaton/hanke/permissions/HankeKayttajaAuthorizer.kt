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

    fun authorizeKayttajaId(hankeKayttajaId: UUID, permissionCode: PermissionCode): Boolean {
        val hankeId = hankekayttajaRepository.findByIdOrNull(hankeKayttajaId)?.hankeId
        authorize(hankeId, permissionCode) { HankeKayttajaNotFoundException(hankeKayttajaId) }
        return true
    }

    fun authorizeKayttajaId(hankeKayttajaId: UUID, permissionCode: String): Boolean =
        authorizeKayttajaId(hankeKayttajaId, PermissionCode.valueOf(permissionCode))
}
