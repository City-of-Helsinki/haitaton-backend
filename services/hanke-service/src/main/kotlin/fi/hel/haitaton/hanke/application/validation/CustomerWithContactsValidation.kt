package fi.hel.haitaton.hanke.application.validation

import fi.hel.haitaton.hanke.application.Contact
import fi.hel.haitaton.hanke.application.Customer
import fi.hel.haitaton.hanke.application.CustomerWithContacts
import fi.hel.haitaton.hanke.application.ordererCount
import fi.hel.haitaton.hanke.isValidBusinessId
import fi.hel.haitaton.hanke.validation.ValidationResult
import fi.hel.haitaton.hanke.validation.Validators.notBlank
import fi.hel.haitaton.hanke.validation.Validators.notJustWhitespace
import fi.hel.haitaton.hanke.validation.Validators.notNull
import fi.hel.haitaton.hanke.validation.Validators.notNullOrBlank
import fi.hel.haitaton.hanke.validation.Validators.validate
import fi.hel.haitaton.hanke.validation.Validators.validateFalse
import fi.hel.haitaton.hanke.validation.Validators.validateTrue

internal fun CustomerWithContacts.validateForErrors(path: String): ValidationResult =
    customer
        .validateForErrors("$path.customer")
        .andAllIn(contacts, "$path.contacts", ::validateContactForErrors)

internal fun Customer.validateForErrors(path: String): ValidationResult =
    validate { notJustWhitespace(name, "$path.name") }
        .and { notJustWhitespace(email, "$path.email") }
        .and { notJustWhitespace(phone, "$path.phone") }
        .whenNotNull(registryKey) { validateTrue(it.isValidBusinessId(), "$path.registryKey") }

private fun validateContactForErrors(contact: Contact, path: String) =
    with(contact) {
        validate { notJustWhitespace(firstName, "$path.firstName") }
            .and { notJustWhitespace(lastName, "$path.lastName") }
            .and { notJustWhitespace(email, "$path.email") }
            .and { notJustWhitespace(phone, "$path.phone") }
    }

internal fun atMostOneOrderer(customersWithContacts: List<CustomerWithContacts>): ValidationResult =
    validateFalse(
        customersWithContacts.ordererCount() > 1,
        "customersWithContacts[].contacts[].orderer"
    )

internal fun CustomerWithContacts.validateForMissing(path: String): ValidationResult =
    customer
        .validateForMissing("$path.customer")
        .andAllIn(contacts, "$path.contacts", ::validateForMissing)

internal fun Customer.validateForMissing(path: String): ValidationResult =
    validate { notNull(type, "$path.type") }.and { notBlank(name, "$path.name") }

private fun validateForMissing(contact: Contact, path: String) = validate {
    notNullOrBlank(contact.fullName(), "$path.firstName")
}

internal fun exactlyOneOrderer(
    customersWithContacts: List<CustomerWithContacts>
): ValidationResult =
    validateTrue(
        customersWithContacts.ordererCount() == 1,
        "customersWithContacts[].contacts[].orderer"
    )
