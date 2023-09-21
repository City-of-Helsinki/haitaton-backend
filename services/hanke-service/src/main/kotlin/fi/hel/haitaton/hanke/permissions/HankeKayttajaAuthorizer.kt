package fi.hel.haitaton.hanke.permissions

import fi.hel.haitaton.hanke.HankeRepository
import org.springframework.stereotype.Component

@Component
class HankeKayttajaAuthorizer(
    permissionService: PermissionService,
    hankeRepository: HankeRepository,
) : Authorizer(permissionService, hankeRepository)
