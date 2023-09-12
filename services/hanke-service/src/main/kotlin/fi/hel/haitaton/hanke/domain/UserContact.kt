package fi.hel.haitaton.hanke.domain

import fi.hel.haitaton.hanke.application.ApplicationContactType

sealed interface UserContact {
    val name: String
    val email: String
}

data class HankeUserContact(override val name: String, override val email: String) : UserContact {
    companion object {
        fun from(name: String?, email: String?): HankeUserContact? =
            when {
                name.isNullOrBlank() || email.isNullOrBlank() -> null
                else -> HankeUserContact(name, email)
            }
    }
}

data class ApplicationUserContact(
    override val name: String,
    override val email: String,
    val type: ApplicationContactType
) : UserContact {
    companion object {
        fun from(
            name: String?,
            email: String?,
            type: ApplicationContactType
        ): ApplicationUserContact? =
            when {
                name.isNullOrBlank() || email.isNullOrBlank() -> null
                else -> ApplicationUserContact(name, email, type)
            }
    }
}
