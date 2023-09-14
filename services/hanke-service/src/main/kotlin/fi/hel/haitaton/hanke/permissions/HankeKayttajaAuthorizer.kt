package fi.hel.haitaton.hanke.permissions

import fi.hel.haitaton.hanke.HankeRepository
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class HankeKayttajaAuthorizer(
    permissionService: PermissionService,
    hankeRepository: HankeRepository,
    private val hankeKayttajaRepository: HankeKayttajaRepository,
) : Authorizer(permissionService, hankeRepository) {
    @Transactional(readOnly = true)
    fun authorizeKayttajaId(hankeKayttajaId: UUID, permissionCode: PermissionCode) {
        val hankeId = hankeKayttajaRepository.findByIdOrNull(hankeKayttajaId)?.hankeId
        authorize(hankeId, permissionCode) { HankeKayttajaNotFoundException(hankeKayttajaId) }
    }
}
