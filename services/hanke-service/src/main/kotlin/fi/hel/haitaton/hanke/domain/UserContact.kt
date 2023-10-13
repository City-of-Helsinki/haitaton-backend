package fi.hel.haitaton.hanke.domain

import fi.hel.haitaton.hanke.application.ApplicationContactType
import fi.hel.haitaton.hanke.application.ApplicationData
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.CustomerWithContacts

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

/**
 * Map application contacts to [ApplicationUserContact] set containing information on contact type.
 */
fun ApplicationData.typedContacts(omit: String? = null): Set<ApplicationUserContact> =
    when (this) {
        is CableReportApplicationData ->
            customersByRole()
                .flatMap { (role, customer) -> customer.contactsTypedAs(role) }
                .remove(omit)
                .toSet()
    }

/** Filter contacts whose email does not exist in the other set. */
fun Set<ApplicationUserContact>.subtractByEmail(
    other: Set<ApplicationUserContact>
): Set<ApplicationUserContact> {
    val otherEmails = other.map { it.email }
    return filterNot { contact -> contact.email in otherEmails }.toSet()
}

private fun List<ApplicationUserContact>.remove(email: String?) =
    if (email == null) this else filter { it.email != email }

private fun CustomerWithContacts.contactsTypedAs(
    type: ApplicationContactType
): List<ApplicationUserContact> =
    contacts.mapNotNull { ApplicationUserContact.from(it.fullName(), it.email, type) }
