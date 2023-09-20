package fi.hel.haitaton.hanke

import fi.hel.haitaton.hanke.permissions.Authorizer
import fi.hel.haitaton.hanke.permissions.PermissionService
import org.springframework.stereotype.Component

@Component
class HankeAuthorizer(
    permissionService: PermissionService,
    hankeRepository: HankeRepository,
) : Authorizer(permissionService, hankeRepository)
