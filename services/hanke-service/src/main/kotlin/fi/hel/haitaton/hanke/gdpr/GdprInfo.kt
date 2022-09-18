package fi.hel.haitaton.hanke.gdpr

data class GdprInfo(
    val name: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val ipAddress: String? = null,
    val organisation: GdprOrganisation? = null,
    val address: GdprAddress? = null,
)

data class GdprOrganisation(
    val id: Int? = null,
    val name: String? = null,
    val registryKey: String? = null,
    val department: String? = null,
)

data class GdprAddress(
    val street: String? = null,
    val city: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
)
