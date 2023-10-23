package fi.hel.haitaton.hanke.permissions

/**
 * Class to use in creation of HankeKayttajas. Provides a unified (non-nullability) functionality
 * for application and hanke contacts.
 */
data class UserContact(val name: String, val email: String) {
    companion object {
        fun from(name: String?, email: String?): UserContact? =
            when {
                name.isNullOrBlank() || email.isNullOrBlank() -> null
                else -> UserContact(name, email)
            }
    }
}
