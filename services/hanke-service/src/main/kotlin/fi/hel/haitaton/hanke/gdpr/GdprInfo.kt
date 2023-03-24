package fi.hel.haitaton.hanke.gdpr

import fi.hel.haitaton.hanke.domain.BusinessId

data class GdprInfo(
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val ipAddress: String? = null,
    val organisation: GdprOrganisation? = null,
)

data class GdprOrganisation(
    val id: Int? = null,
    val name: String? = null,
    val registryKey: BusinessId? = null,
    val department: String? = null,
)
