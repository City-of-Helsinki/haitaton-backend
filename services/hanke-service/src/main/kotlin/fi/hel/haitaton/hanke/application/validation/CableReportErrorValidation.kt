package fi.hel.haitaton.hanke.application.validation

import fi.hel.haitaton.hanke.application.CableReportApplicationData
import fi.hel.haitaton.hanke.application.Contact
import fi.hel.haitaton.hanke.application.Customer
import fi.hel.haitaton.hanke.application.CustomerWithContacts
import fi.hel.haitaton.hanke.application.PostalAddress
import fi.hel.haitaton.hanke.application.ordererCount
import fi.hel.haitaton.hanke.isValidBusinessId
import fi.hel.haitaton.hanke.validation.ValidationResult
import fi.hel.haitaton.hanke.validation.Validators.isBeforeOrEqual
import fi.hel.haitaton.hanke.validation.Validators.notJustWhitespace
import fi.hel.haitaton.hanke.validation.Validators.validate
import fi.hel.haitaton.hanke.validation.Validators.validateFalse
import fi.hel.haitaton.hanke.validation.Validators.validateTrue

/** Validate draft application. Checks only fields that have some actual data. */
fun CableReportApplicationData.validateForErrors(): ValidationResult =
    validate { notJustWhitespace(name, "name") }
        .and { notJustWhitespace(workDescription, "workDescription") }
        .and { atMostOneOrderer(customersWithContacts()) }
        .andWhen(startTime != null && endTime != null) {
            isBeforeOrEqual(startTime!!, endTime!!, "endTime")
        }
        .whenNotNull(postalAddress) { it.validateForErrors("postalAddress") }
        .whenNotNull(customerWithContacts) { it.validateForErrors("customerWithContacts") }
        .whenNotNull(contractorWithContacts) { it.validateForErrors("contractorWithContacts") }
        .whenNotNull(representativeWithContacts) {
            it.validateForErrors("representativeWithContacts")
        }
        .whenNotNull(propertyDeveloperWithContacts) {
            it.validateForErrors("propertyDeveloperWithContacts")
        }

private fun CustomerWithContacts.validateForErrors(path: String): ValidationResult =
    customer
        .validateForErrors("$path.customer")
        .andAllIn(contacts, "$path.contacts", ::validateContactForErrors)

private fun Customer.validateForErrors(path: String): ValidationResult =
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

private fun PostalAddress.validateForErrors(path: String) =
    validate { notJustWhitespace(postalCode, "$path.postalCode") }
        .and { notJustWhitespace(city, "$path.city") }
        .and { notJustWhitespace(streetAddress.streetName, "$path.streetAddress.streetName") }

private fun atMostOneOrderer(customersWithContacts: List<CustomerWithContacts>): ValidationResult =
    validateFalse(
        customersWithContacts.ordererCount() > 1,
        "customersWithContacts[].contacts[].orderer"
    )
