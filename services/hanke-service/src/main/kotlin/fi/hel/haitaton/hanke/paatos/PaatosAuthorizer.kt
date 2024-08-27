package fi.hel.haitaton.hanke.paatos

import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.hakemus.HakemusRepository
import fi.hel.haitaton.hanke.permissions.Authorizer
import fi.hel.haitaton.hanke.permissions.PermissionCode
import fi.hel.haitaton.hanke.permissions.PermissionService
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PaatosAuthorizer(
    permissionService: PermissionService,
    hankeRepository: HankeRepository,
    private val paatosRepository: PaatosRepository,
    private val hakemusRepository: HakemusRepository,
) : Authorizer(permissionService, hankeRepository) {

    @Transactional(readOnly = true)
    fun authorizePaatosId(paatosId: UUID, permissionCode: String): Boolean {
        val hankeId =
            paatosRepository.findByIdOrNull(paatosId)?.let {
                hakemusRepository.findByIdOrNull(it.hakemusId)?.hanke?.id
            }
        authorize(hankeId, PermissionCode.valueOf(permissionCode)) {
            PaatosNotFoundException(paatosId)
        }
        return true
    }
}
