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
import java.util.Locale

/** Validate draft application. Checks only fields that have some actual data. */
fun CableReportApplicationData.validateForErrors(): ValidationResult =
    validate { notJustWhitespace(name, "name") }
        .and { notJustWhitespace(workDescription, "workDescription") }
        .and { notJustWhitespace(customerReference, "customerReference") }
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
        .whenNotNull(invoicingCustomer) { it.validateForErrors("invoicingCustomer") }

private fun CustomerWithContacts.validateForErrors(path: String): ValidationResult =
    customer
        .validateForErrors("$path.customer")
        .andAllIn(contacts, "$path.contacts", ::validateContactForErrors)

private fun Customer.validateForErrors(path: String): ValidationResult =
    validate { notJustWhitespace(name, "$path.name") }
        .and { validateTrue(Locale.getISOCountries().contains(country), "$path.country") }
        .and { notJustWhitespace(email, "$path.email") }
        .and { notJustWhitespace(phone, "$path.phone") }
        .whenNotNull(registryKey) { validateTrue(it.isValidBusinessId(), "$path.registryKey") }
        .and { notJustWhitespace(ovt, "$path.ovt") }
        .and { notJustWhitespace(invoicingOperator, "$path.invoicingOperator") }
        .and { notJustWhitespace(sapCustomerNumber, "$path.sapCustomerNumber") }

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
