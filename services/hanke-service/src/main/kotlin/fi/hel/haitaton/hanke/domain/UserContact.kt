package fi.hel.haitaton.hanke.domain

import fi.hel.haitaton.hanke.application.ApplicationData
import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.CustomerWithContacts

data class UserContact(val name: String, val email: String) {
    companion object {
        fun from(name: String?, email: String?): UserContact? =
            when {
                name.isNullOrBlank() || email.isNullOrBlank() -> null
                else -> UserContact(name, email)
            }
    }
}

/**
 * An extension function to get email addresses from customer contact persons. Returns a set of
 * emails that:
 * - are not null, empty or blank.
 * - do not match the optional [omit] argument.
 */
fun ApplicationData.contactPersonEmails(omit: String? = null): Set<String> =
    when (this) {
        is CableReportApplicationData ->
            customersWithContacts().flatMap { it.contactPersonEmails(omit) }.toSet()
    }

private fun CustomerWithContacts.contactPersonEmails(omit: String?) =
    contacts.mapNotNull { if (it.email.isNullOrBlank() || it.email == omit) null else it.email }
