package fi.hel.haitaton.hanke.application.validation

import fi.hel.haitaton.hanke.application.ExcavationNotificationData
import fi.hel.haitaton.hanke.validation.ValidationResult
import fi.hel.haitaton.hanke.validation.Validators.isBeforeOrEqual
import fi.hel.haitaton.hanke.validation.Validators.notJustWhitespace
import fi.hel.haitaton.hanke.validation.Validators.validate

/** Validate draft application. Checks only fields that have some actual data. */
fun ExcavationNotificationData.validateForErrors(): ValidationResult =
    validate { notJustWhitespace(name, "name") }
        .and { notJustWhitespace(workDescription, "workDescription") }
        .and { atMostOneOrderer(customersWithContacts()) }
        .andWhen(startTime != null && endTime != null) {
            isBeforeOrEqual(startTime!!, endTime!!, "endTime")
        }
        .whenNotNull(customerWithContacts) { it.validateForErrors("customerWithContacts") }
        .whenNotNull(contractorWithContacts) { it.validateForErrors("contractorWithContacts") }
        .whenNotNull(representativeWithContacts) {
            it.validateForErrors("representativeWithContacts")
        }
        .whenNotNull(propertyDeveloperWithContacts) {
            it.validateForErrors("propertyDeveloperWithContacts")
        }
        .whenNotNull(invoicingCustomer) { it.validateForErrors("invoicingCustomer") }
        .whenNotNull(customerReference) { notJustWhitespace(it, "customerReference") }
        .whenNotNull(additionalInfo) { notJustWhitespace(it, "additionalInfo") }
