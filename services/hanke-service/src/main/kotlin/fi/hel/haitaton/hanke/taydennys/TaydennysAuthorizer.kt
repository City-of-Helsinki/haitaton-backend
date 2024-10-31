package fi.hel.haitaton.hanke.taydennys

import fi.hel.haitaton.hanke.HankeRepository
import fi.hel.haitaton.hanke.hakemus.HakemusAuthorizer
import fi.hel.haitaton.hanke.permissions.Authorizer
import fi.hel.haitaton.hanke.permissions.PermissionService
import java.util.UUID
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class TaydennysAuthorizer(
    permissionService: PermissionService,
    hankeRepository: HankeRepository,
    private val taydennysRepository: TaydennysRepository,
    private val hakemusAuthorizer: HakemusAuthorizer,
) : Authorizer(permissionService, hankeRepository) {

    @Transactional(readOnly = true)
    fun authorize(id: UUID, permissionCode: String): Boolean =
        taydennysRepository.findByIdOrNull(id)?.taydennyspyynto?.applicationId?.let {
            hakemusAuthorizer.authorizeHakemusId(it, permissionCode)
        } ?: false
}
